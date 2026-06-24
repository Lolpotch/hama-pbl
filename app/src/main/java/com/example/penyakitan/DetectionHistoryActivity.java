package com.example.penyakitan;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetectionHistoryActivity extends AppCompatActivity {

    private LinearLayout detectionContainer;
    private ScrollView detectionScrollView;
    private TextView tvTotalDetection, tvHighConfidence;
    private TextView tvDetectionHistoryTitle, tvDetectionHistorySubtitle;
    private ImageView btnBackDetectionHistory;

    private DatabaseReference diseaseResultRef;
    private DatabaseReference pestResultRef;
    private DatabaseReference detectionSummaryRef;
    private DatabaseReference handlingSummaryRef;

    private static final int LIMIT_EACH_MODE = 100;
    private static final int MAX_RENDER_ITEMS = 100;

    private final List<DetectionHistoryItem> allDetectionItems = new ArrayList<>();

    private boolean diseaseLoaded = false;
    private boolean pestLoaded = false;
    private boolean showingAll = false;
    private int pendingScrollY = -1;

    private String modeFilter = "all";
    private String handlingFilter = "all";
    private static final String DATABASE_URL =
            "https://lokasighthama-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection_history);

        readModeFilter();

        btnBackDetectionHistory = findViewById(R.id.btnBackDetectionHistory);
        detectionScrollView = findViewById(R.id.detectionScrollView);
        detectionContainer = findViewById(R.id.detectionContainer);
        tvTotalDetection = findViewById(R.id.tvTotalDetection);
        tvHighConfidence = findViewById(R.id.tvHighConfidence);

        tvDetectionHistoryTitle = findViewById(R.id.tvDetectionHistoryTitle);
        tvDetectionHistorySubtitle = findViewById(R.id.tvDetectionHistorySubtitle);

        if (btnBackDetectionHistory != null) {
            btnBackDetectionHistory.setOnClickListener(v -> finish());
        }

        updateHeaderTitle();

        diseaseResultRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("inference_result")
                .child("disease");

        pestResultRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("inference_result")
                .child("pest");

        detectionSummaryRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("summary")
                .child("detection");

        handlingSummaryRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("summary")
                .child("handling");

        showLoadingText();
        loadDetectionCounters();
        loadAllDetections();
    }

    private void readModeFilter() {
        modeFilter = getIntent().getStringExtra("mode_filter");
        handlingFilter = getIntent().getStringExtra("handling_filter");

        if (modeFilter == null || modeFilter.trim().isEmpty()) {
            modeFilter = "all";
        }

        if (handlingFilter == null || handlingFilter.trim().isEmpty()) {
            handlingFilter = "all";
        }

        modeFilter = modeFilter.trim().toLowerCase(Locale.US);
        handlingFilter = handlingFilter.trim().toLowerCase(Locale.US);

        if (!modeFilter.equals("pest")
                && !modeFilter.equals("disease")
                && !modeFilter.equals("all")) {
            modeFilter = "all";
        }

        if (!handlingFilter.equals("handled")
                && !handlingFilter.equals("unhandled")
                && !handlingFilter.equals("all")) {
            handlingFilter = "all";
        }
    }

    private void updateHeaderTitle() {
        if (tvDetectionHistoryTitle == null || tvDetectionHistorySubtitle == null) {
            return;
        }

        if (handlingFilter.equals("unhandled")) {
            tvDetectionHistoryTitle.setText("Belum Ditangani");
            tvDetectionHistorySubtitle.setText("Riwayat deteksi yang masih perlu ditangani");
        } else if (handlingFilter.equals("handled")) {
            tvDetectionHistoryTitle.setText("Sudah Ditangani");
            tvDetectionHistorySubtitle.setText("Riwayat deteksi yang sudah ditandai selesai");
        } else if (modeFilter.equals("pest")) {
            tvDetectionHistoryTitle.setText("Riwayat Deteksi Hama");
            tvDetectionHistorySubtitle.setText("Riwayat hasil deteksi hama pada tanaman selada");
        } else if (modeFilter.equals("disease")) {
            tvDetectionHistoryTitle.setText("Riwayat Deteksi Penyakit");
            tvDetectionHistorySubtitle.setText("Riwayat hasil deteksi penyakit pada tanaman selada");
        } else {
            tvDetectionHistoryTitle.setText("Semua Deteksi");
            tvDetectionHistorySubtitle.setText("Riwayat hasil deteksi hama dan penyakit");
        }
    }

    private void showLoadingText() {
        detectionContainer.removeAllViews();

        TextView loadingText = new TextView(this);
        loadingText.setText("Memuat riwayat deteksi...");
        loadingText.setTextColor(0xFF506F46);
        loadingText.setTextSize(14);
        loadingText.setGravity(android.view.Gravity.CENTER);
        loadingText.setPadding(0, dpToPx(40), 0, dpToPx(40));

        detectionContainer.addView(
                loadingText,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void loadAllDetections() {
        allDetectionItems.clear();
        diseaseLoaded = false;
        pestLoaded = false;

        if (modeFilter.equals("disease")) {
            pestLoaded = true;
            loadDiseaseDetections();

        } else if (modeFilter.equals("pest")) {
            diseaseLoaded = true;
            loadPestDetections();

        } else {
            loadDiseaseDetections();
            loadPestDetections();
        }
    }

    private void loadDetectionCounters() {
        if (handlingFilter.equals("all")) {
            loadDetectionCountersFromSummary();
            return;
        }

        if (modeFilter.equals("all")) {
            loadHandlingCountersFromSummary();
            return;
        }

        final int[] totalCount = {0};
        final int[] highConfidenceCount = {0};
        final boolean[] diseaseDone = {false};
        final boolean[] pestDone = {false};

        if (modeFilter.equals("pest")) {
            diseaseDone[0] = true;
        }

        if (modeFilter.equals("disease")) {
            pestDone[0] = true;
        }

        if (!modeFilter.equals("pest")) {
            diseaseResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int[] result = countDetectionsOnly(snapshot, "disease");


                    totalCount[0] += result[0];
                    highConfidenceCount[0] += result[1];

                    diseaseDone[0] = true;
                    updateCounterIfReady(totalCount[0], highConfidenceCount[0], diseaseDone[0], pestDone[0]);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    diseaseDone[0] = true;
                    updateCounterIfReady(totalCount[0], highConfidenceCount[0], diseaseDone[0], pestDone[0]);
                }
            });
        }

        if (!modeFilter.equals("disease")) {
            pestResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int[] result = countDetectionsOnly(snapshot, "pest");

                    totalCount[0] += result[0];
                    highConfidenceCount[0] += result[1];

                    pestDone[0] = true;
                    updateCounterIfReady(totalCount[0], highConfidenceCount[0], diseaseDone[0], pestDone[0]);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    pestDone[0] = true;
                    updateCounterIfReady(totalCount[0], highConfidenceCount[0], diseaseDone[0], pestDone[0]);
                }
            });
        }
    }

    private void updateCounterIfReady(
            int totalCount,
            int highConfidenceCount,
            boolean diseaseDone,
            boolean pestDone
    ) {
        if (diseaseDone && pestDone) {
            tvTotalDetection.setText(String.valueOf(totalCount));
            tvHighConfidence.setText(String.valueOf(highConfidenceCount));
        }
    }

    private int[] countDetectionsOnly(DataSnapshot snapshot, String defaultMode) {
        int total = 0;
        int highConfidence = 0;

        for (DataSnapshot data : snapshot.getChildren()) {
            String key = data.getKey();

            if (key != null && key.equalsIgnoreCase("latest")) {
                continue;
            }

            if (!matchesHandlingFilter(data)) {
                continue;
            }

            if (defaultMode.equalsIgnoreCase("pest")
                    && data.child("pest").exists()
                    && getBestPestSnapshot(data) == null) {
                continue;
            }

            String className = getDetectionClassName(data, defaultMode);
            String imageUrl = getDetectionImageUrl(data);
            Double confidenceValue = getDetectionConfidence(data, defaultMode);

            if (className == null || className.trim().isEmpty()) {
                continue;
            }

            if (className.equalsIgnoreCase("healthy")
                    || className.equalsIgnoreCase("unknown")) {
                continue;
            }

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                continue;
            }

            double confidencePercent = 0;

            if (confidenceValue != null) {
                confidencePercent = confidenceValue * 100;
            }

            total++;

            if (confidencePercent >= 70) {
                highConfidence++;
            }
        }

        return new int[]{total, highConfidence};
    }

    private void loadDiseaseDetections() {
        Query query = diseaseResultRef.orderByChild("time/timestamp");

        if (!showingAll) {
            query = query.limitToLast(LIMIT_EACH_MODE);
        }

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                parseDetectionSnapshot(snapshot, "disease");
                diseaseLoaded = true;
                renderIfReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                diseaseLoaded = true;

                Toast.makeText(
                        DetectionHistoryActivity.this,
                        "Gagal mengambil data penyakit: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();

                renderIfReady();
            }
        });
    }

    private void loadPestDetections() {
        Query query = pestResultRef.orderByChild("time/timestamp");

        if (!showingAll) {
            query = query.limitToLast(LIMIT_EACH_MODE);
        }

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                parseDetectionSnapshot(snapshot, "pest");
                pestLoaded = true;
                renderIfReady();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pestLoaded = true;
                renderIfReady();
            }
        });
    }

    private void parseDetectionSnapshot(DataSnapshot snapshot, String defaultMode) {
        for (DataSnapshot data : snapshot.getChildren()) {
            String key = data.getKey();

            if (key != null && key.equalsIgnoreCase("latest")) {
                continue;
            }

            if (!matchesHandlingFilter(data)) {
                continue;
            }

            String className = getDetectionClassName(data, defaultMode);
            String imageUrl = getDetectionImageUrl(data);
            String mode = getSafeString(data.child("mode"));
            String recommendation = getRecommendationText(data);
            String source = getDetectionSource(data);
            String timestamp = getDetectionTimestamp(data);
            Double confidenceValue = getDetectionConfidence(data, defaultMode);

            if (recommendation == null || recommendation.trim().isEmpty()) {
                recommendation = "Belum ada rekomendasi penanganan.";
            }

            boolean handled = isDetectionHandled(data);

            if (className == null || className.trim().isEmpty()) {
                className = "Tidak Diketahui";
            }

            if (className.equalsIgnoreCase("healthy")
                    || className.equalsIgnoreCase("unknown")) {
                continue;
            }

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                imageUrl = "placeholder";
            }

            if (mode == null || mode.trim().isEmpty()) {
                mode = defaultMode;
            }

            if (source == null || source.trim().isEmpty()) {
                source = "kamera";
            }

            if (timestamp == null || timestamp.trim().isEmpty()) {
                timestamp = "-";
            }

            double confidencePercent = 0;

            if (confidenceValue != null) {
                confidencePercent = confidenceValue * 100;
            }

            String displayName = formatClassName(className);
            String fileName = getDetectionFileName(data, imageUrl, timestamp);
            String environmentLabel = getDetectionEnvironmentLabel(data);

            String description =
                    "Mode: " + mode +
                            "\nSource: " + source +
                            "\nConfidence: " + String.format(Locale.US, "%.2f", confidencePercent) + "%" +
                            (environmentLabel.trim().isEmpty()
                                    ? ""
                                    : "\nKondisi saat deteksi: " + environmentLabel);

            AlertPanel alert = new AlertPanel(
                    source,
                    displayName,
                    imageUrl,
                    fileName,
                    description,
                    recommendation
            );

            allDetectionItems.add(new DetectionHistoryItem(
                    key,
                    alert,
                    handled,
                    mode,
                    source,
                    String.format(Locale.US, "%.2f", confidencePercent),
                    confidencePercent,
                    environmentLabel,
                    timestamp,
                    getTimestampMillis(timestamp)
            ));
        }
    }

    private void renderIfReady() {
        if (!diseaseLoaded || !pestLoaded) {
            return;
        }

        allDetectionItems.sort(new Comparator<DetectionHistoryItem>() {
            @Override
            public int compare(DetectionHistoryItem o1, DetectionHistoryItem o2) {
                int millisCompare = Long.compare(o2.timestampMillis, o1.timestampMillis);

                if (millisCompare != 0) {
                    return millisCompare;
                }

                String t1 = o1.rawTimestamp == null ? "" : o1.rawTimestamp;
                String t2 = o2.rawTimestamp == null ? "" : o2.rawTimestamp;

                return t2.compareTo(t1);
            }
        });

        List<DetectionHistoryItem> renderList = new ArrayList<>();

        int maxItems = showingAll ? allDetectionItems.size() : MAX_RENDER_ITEMS;

        for (int i = 0; i < allDetectionItems.size(); i++) {
            if (i >= maxItems) {
                break;
            }

            renderList.add(allDetectionItems.get(i));
        }

        showDetectionGrid(renderList);

        if (pendingScrollY >= 0 && detectionScrollView != null) {
            final int targetScrollY = pendingScrollY;
            pendingScrollY = -1;
            detectionScrollView.post(() -> detectionScrollView.scrollTo(0, targetScrollY));
        }
    }

    private void showDetectionGrid(List<DetectionHistoryItem> detectionList) {
        detectionContainer.removeAllViews();

        if (detectionList.isEmpty()) {
            TextView emptyText = new TextView(this);

            if (handlingFilter.equals("unhandled")) {
                emptyText.setText("Tidak ada deteksi yang belum ditangani.");
            } else if (handlingFilter.equals("handled")) {
                emptyText.setText("Belum ada deteksi yang sudah ditangani.");
            } else if (modeFilter.equals("pest")) {
                emptyText.setText("Belum ada riwayat deteksi hama.");
            } else if (modeFilter.equals("disease")) {
                emptyText.setText("Belum ada riwayat deteksi penyakit.");
            } else {
                emptyText.setText("Belum ada riwayat deteksi.");
            }

            emptyText.setTextColor(0xFF506F46);
            emptyText.setTextSize(14);
            emptyText.setGravity(android.view.Gravity.CENTER);
            emptyText.setPadding(0, dpToPx(40), 0, dpToPx(40));

            detectionContainer.addView(
                    emptyText,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            return;
        }

        int columnCount = getHistoryColumnCount();
        LinearLayout currentRow = null;

        for (int i = 0; i < detectionList.size(); i++) {
            int columnIndex = i % columnCount;

            if (columnIndex == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

                rowParams.setMargins(0, 0, 0, dpToPx(12));

                currentRow.setLayoutParams(rowParams);
                detectionContainer.addView(currentRow);
            }

            AlertView view = createDetectionCard(detectionList.get(i));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            );

            int leftMargin = columnIndex == 0 ? 0 : dpToPx(6);
            int rightMargin = columnIndex == columnCount - 1 ? 0 : dpToPx(6);
            cardParams.setMargins(leftMargin, 0, rightMargin, 0);

            view.setLayoutParams(cardParams);

            if (currentRow != null) {
                currentRow.addView(view);
            }
        }

        int lastRowItemCount = detectionList.size() % columnCount;

        if (lastRowItemCount != 0 && detectionContainer.getChildCount() > 0) {
            LinearLayout lastRow = (LinearLayout) detectionContainer.getChildAt(
                    detectionContainer.getChildCount() - 1
            );

            for (int i = lastRowItemCount; i < columnCount; i++) {
                LinearLayout emptySpace = new LinearLayout(this);

                LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                );

                int leftMargin = i == 0 ? 0 : dpToPx(6);
                int rightMargin = i == columnCount - 1 ? 0 : dpToPx(6);
                emptyParams.setMargins(leftMargin, 0, rightMargin, 0);
                emptySpace.setLayoutParams(emptyParams);
                lastRow.addView(emptySpace);
            }
        }

        if (shouldShowLoadAllButton()) {
            detectionContainer.addView(createLoadAllButton());
        }
    }

    private boolean shouldShowLoadAllButton() {
        return !showingAll && allDetectionItems.size() >= MAX_RENDER_ITEMS;
    }

    private TextView createLoadAllButton() {
        TextView button = new TextView(this);
        button.setText("Lihat Semua");
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(14);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_green_button);
        button.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(4), 0, dpToPx(12));
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            pendingScrollY = detectionScrollView == null ? -1 : detectionScrollView.getScrollY();
            showingAll = true;
            button.setText("Memuat...");
            button.setEnabled(false);
            loadAllDetections();
        });

        return button;
    }

    private AlertView createDetectionCard(DetectionHistoryItem item) {
        AlertView view = new AlertView(this);

        AlertPanel alert = item.alert;

        view.setShowModeSourceInDescription(true);

        view.setData(
                alert.imageUrl,
                alert.date,
                alert.diseaseName,
                alert.description,
                alert.solution
        );

        boolean showSolveButton = handlingFilter.equals("unhandled") && !item.handled;

        view.setSolveButtonVisible(showSolveButton);
        view.setImageBadgesVisible(false);
        view.setHandledStatus(item.handled);

        if (showSolveButton) {
            view.setOnSolveClick(() -> markDetectionAsHandled(item));
        }

        view.setOnClickListener(v -> openDetectionDetail(item));

        return view;
    }

    private void loadDetectionCountersFromSummary() {
        detectionSummaryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalCount;
                int highConfidenceCount;

                if (modeFilter.equals("disease")) {
                    totalCount = getIntValue(snapshot.child("disease_count"));
                    highConfidenceCount = getIntValue(snapshot.child("disease_high_confidence_count"));
                } else if (modeFilter.equals("pest")) {
                    totalCount = getIntValue(snapshot.child("pest_count"));
                    highConfidenceCount = getIntValue(snapshot.child("pest_high_confidence_count"));
                } else {
                    totalCount = getIntValue(snapshot.child("total_count"));
                    highConfidenceCount = getIntValue(snapshot.child("high_confidence_count"));
                }

                tvTotalDetection.setText(String.valueOf(totalCount));
                tvHighConfidence.setText(String.valueOf(highConfidenceCount));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvTotalDetection.setText("0");
                tvHighConfidence.setText("0");
            }
        });
    }

    private void loadHandlingCountersFromSummary() {
        handlingSummaryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalCount;
                int highConfidenceCount;

                if (handlingFilter.equals("handled")) {
                    totalCount = getIntValue(snapshot.child("handled_count"));
                    highConfidenceCount = getIntValue(snapshot.child("handled_high_confidence_count"));
                } else {
                    totalCount = getIntValue(snapshot.child("unhandled_count"));
                    highConfidenceCount = getIntValue(snapshot.child("unhandled_high_confidence_count"));
                }

                tvTotalDetection.setText(String.valueOf(totalCount));
                tvHighConfidence.setText(String.valueOf(highConfidenceCount));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvTotalDetection.setText("0");
                tvHighConfidence.setText("0");
            }
        });
    }

    private void openDetectionDetail(DetectionHistoryItem item) {
        AlertPanel alert = item.alert;

        Intent intent = new Intent(
                DetectionHistoryActivity.this,
                DetectionDetailActivity.class
        );

        intent.putExtra("image_url", alert.imageUrl);
        intent.putExtra("file_name", alert.date);
        intent.putExtra("disease_name", alert.diseaseName);
        intent.putExtra("date", item.rawTimestamp);
        intent.putExtra("description", alert.description);
        intent.putExtra("environment_label", item.environmentLabel);
        intent.putExtra("solution", alert.solution);
        intent.putExtra("handled", item.handled);
        intent.putExtra("mode", item.mode);
        intent.putExtra("source", item.source);
        intent.putExtra("confidence", item.confidence);

        startActivity(intent);
    }

    private boolean matchesHandlingFilter(DataSnapshot data) {
        if (handlingFilter.equals("all")) {
            return true;
        }

        boolean handled = isDetectionHandled(data);

        if (handlingFilter.equals("handled")) {
            return handled;
        }

        return !handled;
    }

    private boolean isDetectionHandled(DataSnapshot data) {
        Boolean handledValue = data.child("handled").getValue(Boolean.class);

        if (handledValue != null && handledValue) {
            return true;
        }

        Boolean nestedHandledValue = data.child("status").child("handled").getValue(Boolean.class);

        if (nestedHandledValue != null && nestedHandledValue) {
            return true;
        }

        String status = getSafeString(data.child("status"));

        return status != null && status.equalsIgnoreCase("handled");
    }

    private void markDetectionAsHandled(DetectionHistoryItem item) {
        if (item == null || item.detectionKey == null || item.detectionKey.trim().isEmpty()) {
            Toast.makeText(
                    DetectionHistoryActivity.this,
                    "ID deteksi tidak ditemukan",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        String handledAt = getCurrentIsoTime();

        updates.put("handled", true);
        updates.put("handled_at", handledAt);
        updates.put("status/handled", true);
        updates.put("status/status", "handled");
        updates.put("status/handled_at", handledAt);

        DatabaseReference targetRef = item.mode != null && item.mode.equalsIgnoreCase("pest")
                ? pestResultRef
                : diseaseResultRef;

        targetRef.child(item.detectionKey)
                .updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(
                            DetectionHistoryActivity.this,
                            "Deteksi ditandai selesai",
                            Toast.LENGTH_SHORT
                    ).show();
                    loadDetectionCounters();
                    loadAllDetections();
                })
                .addOnFailureListener(e -> Toast.makeText(
                        DetectionHistoryActivity.this,
                        "Gagal update Firebase: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private String getCurrentIsoTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );

        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        return sdf.format(new Date());
    }

    private String extractFileName(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty() || imageUrl.equals("placeholder")) {
            return "";
        }

        try {
            String cleaned = imageUrl.trim();
            int queryIndex = cleaned.indexOf("?");

            if (queryIndex >= 0) {
                cleaned = cleaned.substring(0, queryIndex);
            }

            int hashIndex = cleaned.indexOf("#");

            if (hashIndex >= 0) {
                cleaned = cleaned.substring(0, hashIndex);
            }

            int slashIndex = cleaned.lastIndexOf("/");

            if (slashIndex >= 0 && slashIndex < cleaned.length() - 1) {
                cleaned = cleaned.substring(slashIndex + 1);
            }

            return URLDecoder.decode(cleaned, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String getDetectionFileName(DataSnapshot data, String fallbackImageUrl, String fallbackText) {
        String fileUrl = getSafeString(data.child("filename"));

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            fileUrl = getSafeString(data.child("image").child("filename"));
        }

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            fileUrl = getSafeString(data.child("input_image_url"));
        }

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            fileUrl = getSafeString(data.child("image").child("input_image_url"));
        }

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            fileUrl = getSafeString(data.child("source_image_url"));
        }

        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            fileUrl = fallbackImageUrl;
        }

        String fileName = extractFileName(fileUrl);

        if (fileName == null || fileName.trim().isEmpty()) {
            return fallbackText == null || fallbackText.trim().isEmpty() ? "-" : fallbackText;
        }

        return fileName;
    }

    private long getTimestampMillis(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) {
            return 0L;
        }

        String text = value.trim();

        try {
            long rawValue = Long.parseLong(text);

            if (rawValue <= 0) {
                return 0L;
            }

            if (rawValue < 100000000000L) {
                return rawValue * 1000L;
            }

            return rawValue;
        } catch (Exception ignored) {
        }

        Date date = parseIsoDate(text);

        if (date == null) {
            date = parseDate(text, "yyyyMMdd_HHmmss", TimeZone.getTimeZone("UTC"));
        }

        if (date == null) {
            date = parseDate(text, "yyyy-MM-dd HH:mm:ss", TimeZone.getDefault());
        }

        return date == null ? 0L : date.getTime();
    }

    private Date parseIsoDate(String value) {
        String normalized = value;

        if (normalized.matches(".*\\.\\d{4,}([+-]\\d{2}:\\d{2}|Z)$")) {
            normalized = normalized.replaceFirst("\\.(\\d{3})\\d+([+-]\\d{2}:\\d{2}|Z)$", ".$1$2");
        }

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String pattern : patterns) {
            TimeZone timeZone = pattern.endsWith("'Z'")
                    ? TimeZone.getTimeZone("UTC")
                    : TimeZone.getDefault();
            Date date = parseDate(normalized, pattern, timeZone);

            if (date != null) {
                return date;
            }
        }

        return null;
    }

    private Date parseDate(String value, String pattern, TimeZone timeZone) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            format.setTimeZone(timeZone);
            return format.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String getDetectionClassName(DataSnapshot data, String defaultMode) {
        if (defaultMode.equalsIgnoreCase("pest")) {
            DataSnapshot bestPest = getBestPestSnapshot(data);

            if (bestPest != null) {
                String pestClassName = getSafeString(bestPest.child("class_name"));

                if (pestClassName == null || pestClassName.trim().isEmpty()) {
                    pestClassName = getSafeString(bestPest.child("detected_class"));
                }

                if (pestClassName == null || pestClassName.trim().isEmpty()) {
                    pestClassName = getSafeString(bestPest.child("label"));
                }

                if (pestClassName != null && !pestClassName.trim().isEmpty()) {
                    return pestClassName;
                }
            }
        }

        String className = getSafeString(data.child("class_name"));

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("detected_class"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("prediction"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("label"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("disease_name"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("result").child("class_name"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("result").child("detected_class"));
        }

        if (className == null || className.trim().isEmpty()) {
            if (defaultMode.equalsIgnoreCase("pest")) {
                className = "Hama Terdeteksi";
            } else if (defaultMode.equalsIgnoreCase("disease")) {
                className = "Penyakit Terdeteksi";
            } else {
                className = "Deteksi";
            }
        }

        return className;
    }

    private Double getDetectionConfidence(DataSnapshot data, String defaultMode) {
        if (defaultMode.equalsIgnoreCase("pest")) {
            DataSnapshot bestPest = getBestPestSnapshot(data);

            if (bestPest != null) {
                Double pestConfidence = getConfidenceFraction(bestPest);

                if (pestConfidence != null) {
                    return pestConfidence;
                }
            }
        }

        Double confidence = getConfidenceFraction(data);

        if (confidence != null) {
            return confidence;
        }

        confidence = getConfidenceFraction(data.child("result"));

        if (confidence != null) {
            return confidence;
        }

        confidence = getConfidenceFraction(data.child("prediction"));

        if (confidence != null) {
            return confidence;
        }

        confidence = data.child("prediction").child("score").getValue(Double.class);

        return normalizeConfidenceFraction(confidence);
    }

    private String getDetectionImageUrl(DataSnapshot data) {
        return getFirstSafeString(
                data,
                "image/annotated_image_url",
                "image/image_url",
                "image/input_image_url",
                "annotated_image_url",
                "image_url",
                "image",
                "imageUrl",
                "url",
                "result/image_url"
        );
    }

    private String getDetectionSource(DataSnapshot data) {
        String source = getSafeString(data.child("source_info").child("source"));

        if (source == null || source.trim().isEmpty()) {
            source = getSafeString(data.child("source"));
        }

        if (source == null || source.trim().isEmpty()) {
            source = getSafeString(data.child("source_info").child("mode"));
        }

        return source;
    }

    private String getDetectionTimestamp(DataSnapshot data) {
        String timestamp = getSafeString(data.child("time").child("created_at_iso"));

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("timestamp_iso"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("created_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("timestamp"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("timestamp"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("created_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("uploaded_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("captured_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getTimestampFromImageFields(data);
        }

        return timestamp;
    }

    private String getDetectionEnvironmentLabel(DataSnapshot data) {
        Double temperature = getDoubleValue(data.child("environment").child("temperature"));
        Double humidity = getDoubleValue(data.child("environment").child("humidity"));

        if (temperature == null) {
            temperature = getDoubleValue(data.child("sensor_snapshot").child("temperature"));
        }

        if (humidity == null) {
            humidity = getDoubleValue(data.child("sensor_snapshot").child("humidity"));
        }

        if (temperature == null) {
            temperature = getDoubleValue(data.child("temperature"));
        }

        if (humidity == null) {
            humidity = getDoubleValue(data.child("humidity"));
        }

        List<String> parts = new ArrayList<>();

        if (temperature != null) {
            parts.add(String.format(Locale.US, "Suhu %.1f°C", temperature));
        }

        if (humidity != null) {
            parts.add(String.format(Locale.US, "RH %.1f%%", humidity));
        }

        if (parts.isEmpty()) {
            return "";
        }

        return String.join(" · ", parts);
    }

    private String getTimestampFromImageFields(DataSnapshot data) {
        String[] imageValues = {
                getSafeString(data.child("input_image_url")),
                getSafeString(data.child("image").child("input_image_url")),
                getSafeString(data.child("image").child("image_url")),
                getSafeString(data.child("image_url")),
                getSafeString(data.child("image"))
        };

        for (String imageValue : imageValues) {
            String timestamp = extractCompactTimestamp(imageValue);

            if (timestamp != null && !timestamp.trim().isEmpty()) {
                return timestamp;
            }
        }

        return "";
    }

    private String extractCompactTimestamp(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        Matcher matcher = Pattern.compile("(\\d{8}_\\d{6})").matcher(value);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    private DataSnapshot getBestPestSnapshot(DataSnapshot data) {
        DataSnapshot bestDetectionSnapshot = data.child("prediction").child("best_detection");

        if (bestDetectionSnapshot.exists()) {
            Double confidence = getConfidenceFraction(bestDetectionSnapshot);
            if (confidence != null && isDetectedPestItem(bestDetectionSnapshot, confidence)) {
                return bestDetectionSnapshot;
            }
        }

        DataSnapshot pestSnapshot = data.child("pest");

        if (!pestSnapshot.exists()) {
            return null;
        }

        DataSnapshot bestSnapshot = null;
        double bestConfidence = -1;

        for (DataSnapshot pestItem : pestSnapshot.getChildren()) {
            Double confidence = getConfidenceFraction(pestItem);

            if (confidence == null) {
                continue;
            }

            if (!isDetectedPestItem(pestItem, confidence)) {
                continue;
            }

            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestSnapshot = pestItem;
            }
        }

        return bestSnapshot;
    }

    private boolean isDetectedPestItem(DataSnapshot pestItem, double confidence) {
        Boolean detected = pestItem.child("is_detected").getValue(Boolean.class);

        if (detected != null) {
            return detected;
        }

        Double threshold = getThresholdFraction(pestItem);
        return threshold == null || confidence >= threshold;
    }

    private Double getConfidenceFraction(DataSnapshot snapshot) {
        Double confidencePercent = snapshot.child("confidence_percent").getValue(Double.class);

        if (confidencePercent != null) {
            return confidencePercent / 100;
        }

        return normalizeConfidenceFraction(snapshot.child("confidence").getValue(Double.class));
    }

    private Double getThresholdFraction(DataSnapshot snapshot) {
        Double thresholdPercent = snapshot.child("threshold_percent").getValue(Double.class);

        if (thresholdPercent != null) {
            return thresholdPercent / 100;
        }

        return normalizeConfidenceFraction(snapshot.child("threshold").getValue(Double.class));
    }

    private Double normalizeConfidenceFraction(Double value) {
        if (value == null) {
            return null;
        }

        return value > 1 ? value / 100 : value;
    }

    private String formatClassName(String className) {
        if (className == null || className.trim().isEmpty()) {
            return "Tidak Diketahui";
        }

        String cleaned = className.replace("_", " ");
        String[] words = cleaned.split(" ");

        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                builder.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return builder.toString().trim();
    }

    private String getSafeString(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return "";
        }

        Object value = snapshot.getValue();

        if (value == null) {
            return "";
        }

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;

            String[] keys = {
                    "summary",
                    "class_name",
                    "predicted_class",
                    "detected_class",
                    "label",
                    "prediction",
                    "value",
                    "annotated_image_url",
                    "url",
                    "image_url",
                    "input_image_url",
                    "image",
                    "path",
                    "public_url",
                    "source",
                    "type",
                    "name",
                    "status",
                    "sync_status",
                    "timestamp_iso",
                    "created_at_iso",
                    "timestamp",
                    "created_at",
                    "time"
            };

            for (String key : keys) {
                Object mappedValue = map.get(key);

                if (mappedValue != null) {
                    return String.valueOf(mappedValue);
                }
            }

            return "";
        }

        return String.valueOf(value);
    }

    private String getRecommendationText(DataSnapshot data) {
        StringBuilder builder = new StringBuilder();
        DataSnapshot recommendation = data.child("recommendation");

        appendRecommendationActions(builder, recommendation.child("actions"));
        appendRecommendationActions(builder, recommendation.child("recommended_actions"));

        String disclaimer = getSafeString(recommendation.child("disclaimer"));

        if (disclaimer == null || disclaimer.trim().isEmpty()) {
            disclaimer = getSafeString(data.child("disclaimer"));
        }

        if (disclaimer != null && !disclaimer.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("Catatan: ").append(disclaimer.trim());
        }

        if (builder.length() > 0) {
            return builder.toString().trim();
        }

        return "";
    }

    private void appendRecommendationActions(StringBuilder builder, DataSnapshot actionsSnapshot) {
        if (actionsSnapshot == null || !actionsSnapshot.exists()) {
            return;
        }

        if (!actionsSnapshot.hasChildren()) {
            String action = getSafeString(actionsSnapshot);

            if (action != null && !action.trim().isEmpty()) {
                builder.append("- ").append(action.trim()).append("\n");
            }
            return;
        }

        for (DataSnapshot actionSnapshot : actionsSnapshot.getChildren()) {
            String action = getActionText(actionSnapshot);

            if (action != null && !action.trim().isEmpty()) {
                builder.append("- ").append(action.trim()).append("\n");
            }
        }
    }

    private String getActionText(DataSnapshot snapshot) {
        String action = getSafeString(snapshot);

        if (action != null && !action.trim().isEmpty()) {
            return action;
        }

        return getFirstSafeString(
                snapshot,
                "action",
                "text",
                "title",
                "description",
                "value"
        );
    }

    private String getFirstSafeString(DataSnapshot data, String... paths) {
        for (String path : paths) {
            String value = getSafeString(data.child(path));

            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private int getIntValue(DataSnapshot snapshot) {
        Integer intValue = snapshot.getValue(Integer.class);

        if (intValue != null) {
            return intValue;
        }

        Long longValue = snapshot.getValue(Long.class);

        if (longValue != null) {
            return longValue.intValue();
        }

        String text = getSafeString(snapshot);

        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private Double getDoubleValue(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }

        Object value = snapshot.getValue();

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof String) {
            try {
                String text = ((String) value).trim();
                return text.isEmpty() ? null : Double.parseDouble(text);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int getHistoryColumnCount() {
        return getResources().getBoolean(R.bool.is_tablet_layout) ? 3 : 2;
    }

    private static class DetectionHistoryItem {
        String detectionKey;
        AlertPanel alert;
        boolean handled;
        String mode;
        String source;
        String confidence;
        double confidenceValue;
        String environmentLabel;
        String rawTimestamp;
        long timestampMillis;

        DetectionHistoryItem(
                String detectionKey,
                AlertPanel alert,
                boolean handled,
                String mode,
                String source,
                String confidence,
                double confidenceValue,
                String environmentLabel,
                String rawTimestamp,
                long timestampMillis
        ) {
            this.detectionKey = detectionKey;
            this.alert = alert;
            this.handled = handled;
            this.mode = mode;
            this.source = source;
            this.confidence = confidence;
            this.confidenceValue = confidenceValue;
            this.environmentLabel = environmentLabel;
            this.rawTimestamp = rawTimestamp;
            this.timestampMillis = timestampMillis;
        }
    }
}
