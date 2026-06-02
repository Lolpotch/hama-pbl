const { onValueCreated, onValueUpdated } = require("firebase-functions/v2/database");
const { onRequest } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");

initializeApp();
setGlobalOptions({ maxInstances: 10 });

const APP_TIME_ZONE = "Asia/Jakarta";

function getDateKeyInAppTimeZone(timestampMs = Date.now()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: APP_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(timestampMs));
}

function isMobileSource(data) {
  const label = String(data.label || "").toLowerCase();
  const source = String(data.source || "").toLowerCase();
  const filename = String(data.filename || "").toLowerCase();

  return (
    label.includes("mobile") ||
    label.includes("hp") ||
    source.includes("mobile") ||
    source.includes("hp") ||
    filename.includes("mobile") ||
    filename.includes("hp_") ||
    filename.includes("_hp") ||
    filename.startsWith("hp")
  );
}

exports.updateCameraSummaryOnCreate = onValueCreated(
  {
    ref: "/camera_captures/{captureId}",
    region: "asia-southeast1",
    instance: "lokasighthama-default-rtdb",
  },
  async (event) => {
    const data = event.data.val();

    if (!data || !data.image) {
      return;
    }

    const db = getDatabase();
    const summaryRef = db.ref("/summary/camera");

    const isMobile = isMobileSource(data);
    const todayDate = getDateKeyInAppTimeZone();
    const captureDate = getDateKeyInAppTimeZone(getCameraTimestampMs(data));
    const isToday = captureDate === todayDate;

    const updates = {};
    updates.total_count = 1;

    if (isMobile) {
      updates.mobile_count = 1;
      updates.mobile_today = isToday ? 1 : 0;
    } else {
      updates.cctv_count = 1;
      updates.cctv_today = isToday ? 1 : 0;
    }

    if (isToday) {
      updates.today_count = 1;
    }

    await summaryRef.transaction((current) => {
      current = current || {};
      const currentTodayDate = current.today_date || "";
      const shouldResetToday = currentTodayDate !== todayDate;

      const currentTodayCount = shouldResetToday ? 0 : (current.today_count || 0);
      const currentMobileToday = shouldResetToday ? 0 : (current.mobile_today || 0);
      const currentCctvToday = shouldResetToday ? 0 : (current.cctv_today || 0);

      return {
        total_count: (current.total_count || 0) + updates.total_count,
        today_count: currentTodayCount + (updates.today_count || 0),
        mobile_count: (current.mobile_count || 0) + (updates.mobile_count || 0),
        cctv_count: (current.cctv_count || 0) + (updates.cctv_count || 0),
        mobile_today: currentMobileToday + (updates.mobile_today || 0),
        cctv_today: currentCctvToday + (updates.cctv_today || 0),
        today_date: todayDate,
        updated_at: Date.now(),
      };
    });
  }
);

function getCameraTimestampMs(data) {
  const candidates = [
    data?.uploaded_at,
    data?.timestamp,
    data?.created_at,
    data?.time,
    data?.filename,
  ];

  for (const candidate of candidates) {
    if (candidate === undefined || candidate === null) {
      continue;
    }

    if (typeof candidate === "number") {
      return candidate > 1000000000000 ? candidate : candidate * 1000;
    }

    const text = String(candidate).trim();

    if (!text) {
      continue;
    }

    const numeric = Number(text);

    if (Number.isFinite(numeric)) {
      return numeric > 1000000000000 ? numeric : numeric * 1000;
    }

    const parsed = Date.parse(text);

    if (Number.isFinite(parsed)) {
      return parsed;
    }

    const compactMatch = text.match(/(20\d{6})[_-](\d{6})/);

    if (compactMatch) {
      const datePart = compactMatch[1];
      const timePart = compactMatch[2];
      const isoText =
        `${datePart.slice(0, 4)}-${datePart.slice(4, 6)}-${datePart.slice(6, 8)}` +
        `T${timePart.slice(0, 2)}:${timePart.slice(2, 4)}:${timePart.slice(4, 6)}+07:00`;
      const compactParsed = Date.parse(isoText);

      if (Number.isFinite(compactParsed)) {
        return compactParsed;
      }
    }
  }

  return Date.now();
}

async function countExistingCameraCaptures() {
  const snapshot = await getDatabase().ref("/camera_captures").once("value");
  const todayDate = getDateKeyInAppTimeZone();
  const summary = {
    total_count: 0,
    today_count: 0,
    mobile_count: 0,
    cctv_count: 0,
    mobile_today: 0,
    cctv_today: 0,
    today_date: todayDate,
    updated_at: Date.now(),
  };

  snapshot.forEach((child) => {
    const data = child.val();

    if (!data || !data.image) {
      return;
    }

    const mobile = isMobileSource(data);
    const captureDate = getDateKeyInAppTimeZone(getCameraTimestampMs(data));

    summary.total_count++;

    if (mobile) {
      summary.mobile_count++;
    } else {
      summary.cctv_count++;
    }

    if (captureDate === todayDate) {
      summary.today_count++;

      if (mobile) {
        summary.mobile_today++;
      } else {
        summary.cctv_today++;
      }
    }
  });

  return summary;
}

function getDetectionClassName(data, mode) {
  if (mode === "pest") {
    const bestDetection = data?.prediction?.best_detection;

    if (bestDetection) {
      return (
        bestDetection.class_name ||
        bestDetection.detected_class ||
        bestDetection.label ||
        ""
      );
    }
  }

  const prediction = data?.prediction;

  if (typeof prediction === "string") {
    return prediction;
  }

  return (
    data?.class_name ||
    data?.detected_class ||
    data?.label ||
    data?.disease_name ||
    data?.result?.class_name ||
    data?.result?.detected_class ||
    prediction?.class_name ||
    prediction?.detected_class ||
    prediction?.label ||
    ""
  );
}

function getDetectionImageUrl(data) {
  const image = data?.image;

  if (typeof image === "string") {
    return image;
  }

  return (
    data?.image_url ||
    data?.imageUrl ||
    data?.url ||
    data?.result?.image_url ||
    image?.annotated_image_url ||
    image?.image_url ||
    image?.input_image_url ||
    ""
  );
}

function isValidDetection(data, mode) {
  if (!data) {
    return false;
  }

  const className = String(getDetectionClassName(data, mode)).trim().toLowerCase();
  const imageUrl = String(getDetectionImageUrl(data)).trim();

  return (
    imageUrl.length > 0 &&
    className.length > 0 &&
    className !== "healthy" &&
    className !== "unknown"
  );
}

function isDetectionHandled(data) {
  if (!data) {
    return false;
  }

  if (data.handled === true) {
    return true;
  }

  if (typeof data.status === "string") {
    return data.status.toLowerCase() === "handled";
  }

  if (data.status && data.status.handled === true) {
    return true;
  }

  return false;
}

function getDetectionConfidence(data, mode) {
  let confidence;

  if (mode === "pest") {
    confidence = data?.prediction?.best_detection?.confidence;
  }

  if (confidence === undefined || confidence === null) {
    confidence =
      data?.confidence ??
      data?.result?.confidence ??
      data?.prediction?.confidence ??
      data?.prediction?.score;
  }

  const numeric = Number(confidence);

  if (!Number.isFinite(numeric)) {
    return 0;
  }

  return numeric > 1 ? numeric : numeric * 100;
}

function getDetectionSource(data) {
  return (
    data?.source ||
    data?.source_info?.source ||
    data?.source_info?.display_source ||
    data?.image?.input_method ||
    "kamera"
  );
}

function getDetectionTimestamp(data) {
  return (
    data?.time?.timestamp_iso ||
    data?.time?.created_at_iso ||
    data?.timestamp_iso ||
    data?.created_at_iso ||
    data?.time?.timestamp ||
    data?.time?.created_at ||
    data?.timestamp ||
    data?.created_at ||
    ""
  );
}

function formatTitleText(text) {
  return String(text || "Deteksi")
    .replace(/_/g, " ")
    .split(" ")
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

function createNotificationItem(data, mode, detectionId) {
  const className = getDetectionClassName(data, mode);
  const confidence = getDetectionConfidence(data, mode);
  const source = getDetectionSource(data);
  const timestamp = getDetectionTimestamp(data);

  return {
    detection_id: detectionId,
    mode,
    title: `${formatTitleText(className)} Terdeteksi`,
    message: `Terdeteksi dari ${source} dengan confidence ${confidence.toFixed(1)}%`,
    source,
    timestamp,
    timestamp_ms: getDetectionTimestampMs(data),
    priority: confidence >= 80 ? "High" : confidence >= 60 ? "Medium" : "Low",
    confidence,
    handled: isDetectionHandled(data),
    created_at: Date.now(),
  };
}

function getDetectionTimestampMs(data) {
  const candidates = [
    data?.time?.timestamp_iso,
    data?.time?.created_at_iso,
    data?.timestamp_iso,
    data?.created_at_iso,
    data?.time?.timestamp,
    data?.time?.created_at,
    data?.timestamp,
    data?.created_at,
  ];

  for (const candidate of candidates) {
    if (candidate === undefined || candidate === null) {
      continue;
    }

    if (typeof candidate === "number") {
      return candidate > 1000000000000 ? candidate : candidate * 1000;
    }

    const text = String(candidate).trim();
    const parsed = Date.parse(text);

    if (Number.isFinite(parsed)) {
      return parsed;
    }

    const compactMatch = text.match(/(20\d{6})[_-](\d{6})/);

    if (compactMatch) {
      const datePart = compactMatch[1];
      const timePart = compactMatch[2];
      const isoText =
        `${datePart.slice(0, 4)}-${datePart.slice(4, 6)}-${datePart.slice(6, 8)}` +
        `T${timePart.slice(0, 2)}:${timePart.slice(2, 4)}:${timePart.slice(4, 6)}+07:00`;
      const compactParsed = Date.parse(isoText);

      if (Number.isFinite(compactParsed)) {
        return compactParsed;
      }
    }
  }

  return Date.now();
}

function isHighConfidence(data, mode) {
  return getDetectionConfidence(data, mode) >= 70;
}

async function incrementSummary(path, values) {
  const db = getDatabase();
  const ref = db.ref(path);

  await ref.transaction((current) => {
    current = current || {};

    const next = { ...current };

    for (const [key, value] of Object.entries(values)) {
      next[key] = Math.max(0, (next[key] || 0) + value);
    }

    next.updated_at = Date.now();
    return next;
  });
}

exports.updateDetectionSummaryOnCreate = onValueCreated(
  {
    ref: "/inference_result/{mode}/{detectionId}",
    region: "asia-southeast1",
    instance: "lokasighthama-default-rtdb",
  },
  async (event) => {
    const mode = event.params.mode;

    if (mode !== "disease" && mode !== "pest") {
      return;
    }

    const data = event.data.val();

    if (!isValidDetection(data, mode)) {
      return;
    }

    const detectionUpdates = {
      total_count: 1,
    };
    detectionUpdates[`${mode}_count`] = 1;

    if (isHighConfidence(data, mode)) {
      detectionUpdates.high_confidence_count = 1;
      detectionUpdates[`${mode}_high_confidence_count`] = 1;
    }

    await incrementSummary("/summary/detection", detectionUpdates);

    if (isDetectionHandled(data)) {
      const updates = { handled_count: 1 };

      if (isHighConfidence(data, mode)) {
        updates.handled_high_confidence_count = 1;
      }

      await incrementSummary("/summary/handling", updates);
    } else {
      const updates = { unhandled_count: 1 };

      if (isHighConfidence(data, mode)) {
        updates.unhandled_high_confidence_count = 1;
      }

      await incrementSummary("/summary/handling", updates);
    }
  }
);

exports.createNotificationOnDetectionCreate = onValueCreated(
  {
    ref: "/inference_result/{mode}/{detectionId}",
    region: "asia-southeast1",
    instance: "lokasighthama-default-rtdb",
  },
  async (event) => {
    const mode = event.params.mode;
    const detectionId = event.params.detectionId;

    if (mode !== "disease" && mode !== "pest") {
      return;
    }

    const data = event.data.val();

    if (!isValidDetection(data, mode)) {
      return;
    }

    await getDatabase()
      .ref(`/notification_items/${mode}_${detectionId}`)
      .set(createNotificationItem(data, mode, detectionId));
  }
);

exports.updateHandlingSummaryOnStatusChange = onValueUpdated(
  {
    ref: "/inference_result/{mode}/{detectionId}",
    region: "asia-southeast1",
    instance: "lokasighthama-default-rtdb",
  },
  async (event) => {
    const mode = event.params.mode;

    if (mode !== "disease" && mode !== "pest") {
      return;
    }

    const before = event.data.before.val();
    const after = event.data.after.val();

    if (!isValidDetection(after, mode)) {
      return;
    }

    const wasHandled = isDetectionHandled(before);
    const isHandled = isDetectionHandled(after);

    if (wasHandled === isHandled) {
      return;
    }

    await getDatabase()
      .ref(`/notification_items/${mode}_${event.params.detectionId}`)
      .update({
        handled: isHandled,
        handled_at:
          after?.handled_at ||
          after?.status?.handled_at ||
          new Date().toISOString(),
        updated_at: Date.now(),
      });

    const highConfidenceDelta = isHighConfidence(after, mode) ? 1 : 0;

    if (isHandled) {
      await incrementSummary("/summary/handling", {
        unhandled_count: -1,
        handled_count: 1,
        unhandled_high_confidence_count: -highConfidenceDelta,
        handled_high_confidence_count: highConfidenceDelta,
      });
    } else {
      await incrementSummary("/summary/handling", {
        unhandled_count: 1,
        handled_count: -1,
        unhandled_high_confidence_count: highConfidenceDelta,
        handled_high_confidence_count: -highConfidenceDelta,
      });
    }
  }
);

async function countExistingDetectionsByMode(mode) {
  const snapshot = await getDatabase()
    .ref(`/inference_result/${mode}`)
    .once("value");

  let totalCount = 0;
  let handledCount = 0;
  let unhandledCount = 0;
  let highConfidenceCount = 0;
  let handledHighConfidenceCount = 0;
  let unhandledHighConfidenceCount = 0;

  snapshot.forEach((child) => {
    const key = child.key || "";
    const data = child.val();

    if (key.toLowerCase() === "latest" || !isValidDetection(data, mode)) {
      return;
    }

    totalCount++;
    const highConfidence = isHighConfidence(data, mode);

    if (highConfidence) {
      highConfidenceCount++;
    }

    if (isDetectionHandled(data)) {
      handledCount++;

      if (highConfidence) {
        handledHighConfidenceCount++;
      }
    } else {
      unhandledCount++;

      if (highConfidence) {
        unhandledHighConfidenceCount++;
      }
    }
  });

  return {
    totalCount,
    handledCount,
    unhandledCount,
    highConfidenceCount,
    handledHighConfidenceCount,
    unhandledHighConfidenceCount,
  };
}

exports.rebuildDetectionSummary = onRequest(
  {
    region: "asia-southeast1",
  },
  async (req, res) => {
    if (req.query.confirm !== "rebuild-detection-summary") {
      res.status(403).send("Missing confirm token.");
      return;
    }

    const disease = await countExistingDetectionsByMode("disease");
    const pest = await countExistingDetectionsByMode("pest");
    const now = Date.now();

    const summary = {
      detection: {
        total_count: disease.totalCount + pest.totalCount,
        disease_count: disease.totalCount,
        pest_count: pest.totalCount,
        high_confidence_count: disease.highConfidenceCount + pest.highConfidenceCount,
        disease_high_confidence_count: disease.highConfidenceCount,
        pest_high_confidence_count: pest.highConfidenceCount,
        updated_at: now,
      },
      handling: {
        unhandled_count: disease.unhandledCount + pest.unhandledCount,
        handled_count: disease.handledCount + pest.handledCount,
        unhandled_high_confidence_count:
          disease.unhandledHighConfidenceCount + pest.unhandledHighConfidenceCount,
        handled_high_confidence_count:
          disease.handledHighConfidenceCount + pest.handledHighConfidenceCount,
        updated_at: now,
      },
    };

    await getDatabase().ref("/summary").update(summary);
    res.json(summary);
  }
);

exports.rebuildCameraSummary = onRequest(
  {
    region: "asia-southeast1",
  },
  async (req, res) => {
    if (req.query.confirm !== "rebuild-camera-summary") {
      res.status(403).send("Missing confirm token.");
      return;
    }

    const summary = await countExistingCameraCaptures();

    await getDatabase().ref("/summary/camera").set(summary);
    res.json({ camera: summary });
  }
);

exports.rebuildNotificationItems = onRequest(
  {
    region: "asia-southeast1",
  },
  async (req, res) => {
    if (req.query.confirm !== "rebuild-notification-items") {
      res.status(403).send("Missing confirm token.");
      return;
    }

    const db = getDatabase();
    const updates = {};
    let count = 0;

    for (const mode of ["disease", "pest"]) {
      const snapshot = await db.ref(`/inference_result/${mode}`).once("value");

      snapshot.forEach((child) => {
        const detectionId = child.key || "";
        const data = child.val();

        if (
          detectionId.toLowerCase() === "latest" ||
          !isValidDetection(data, mode)
        ) {
          return;
        }

        updates[`/notification_items/${mode}_${detectionId}`] =
          createNotificationItem(data, mode, detectionId);
        count++;
      });
    }

    if (count > 0) {
      await db.ref().update(updates);
    }

    res.json({ count });
  }
);
