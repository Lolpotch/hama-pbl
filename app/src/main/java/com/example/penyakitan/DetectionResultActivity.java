package com.example.penyakitan;

import android.graphics.Bitmap;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetectionResultActivity extends AppCompatActivity {

    private ImageView imgResult;
    private ImageView btnClose;

    private TextView txtStatus;
    private TextView btnRetryPendingUpload;
    private TextView tvResultFileName;
    private TextView tvResultDate;
    private TextView tvResultObject;
    private LinearLayout cardDiseaseResult;
    private LinearLayout cardPestResult;
    private TextView txtDiseaseName;
    private TextView txtDiseaseConfidence;
    private TextView txtDiseaseEnvironment;
    private TextView txtDiseaseDescription;
    private TextView txtDiseaseSolution;

    private TextView txtPestName;
    private TextView txtPestConfidence;
    private TextView txtPestEnvironment;
    private TextView txtPestSolution;

    private TextView txtRawResponse;
    private String scanMode = "disease";

    private static DetectionResultActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection_result);

        instance = this;

        imgResult = findViewById(R.id.imgResult);
        btnClose = findViewById(R.id.btnClose);

        txtStatus = findViewById(R.id.txtStatus);
        btnRetryPendingUpload = findViewById(R.id.btnRetryPendingUpload);
        tvResultFileName = findViewById(R.id.tvResultFileName);
        tvResultDate = findViewById(R.id.tvResultDate);
        tvResultObject = findViewById(R.id.tvResultObject);

        cardDiseaseResult = findViewById(R.id.cardDiseaseResult);
        cardPestResult = findViewById(R.id.cardPestResult);

        txtDiseaseName = findViewById(R.id.txtDiseaseName);
        txtDiseaseConfidence = findViewById(R.id.txtDiseaseConfidence);
        txtDiseaseEnvironment = findViewById(R.id.txtDiseaseEnvironment);
        txtDiseaseDescription = findViewById(R.id.txtDiseaseDescription);
        txtDiseaseSolution = findViewById(R.id.txtDiseaseSolution);

        txtPestName = findViewById(R.id.txtPestName);
        txtPestConfidence = findViewById(R.id.txtPestConfidence);
        txtPestEnvironment = findViewById(R.id.txtPestEnvironment);
        txtPestSolution = findViewById(R.id.txtPestSolution);

        txtRawResponse = findViewById(R.id.txtRawResponse);

        readScanModeFromIntent();
        applyResultModeVisibility();
        updateResultHeader(null);
        showImageFromIntent();
        showResultFromIntent();
        setupRetryPendingUploadButton();

        btnClose.setOnClickListener(v -> finish());
    }

    private void readScanModeFromIntent() {
        String modeFromIntent = getIntent().getStringExtra("scan_mode");

        if (modeFromIntent != null && !modeFromIntent.trim().isEmpty()) {
            scanMode = modeFromIntent.trim().toLowerCase();
        }

        if (!scanMode.equals("pest") && !scanMode.equals("disease")) {
            scanMode = "disease";
        }
    }

    private void applyResultModeVisibility() {
        if (scanMode.equals("pest")) {
            cardDiseaseResult.setVisibility(View.GONE);
            cardPestResult.setVisibility(View.VISIBLE);
            tvResultObject.setText("Hama");
        } else {
            cardDiseaseResult.setVisibility(View.VISIBLE);
            cardPestResult.setVisibility(View.GONE);
            tvResultObject.setText("Analisis Daun");
        }
    }

    private void showDiseaseConfidence() {
        txtDiseaseConfidence.setVisibility(View.VISIBLE);
    }

    private void hideDiseaseConfidence() {
        txtDiseaseConfidence.setText("");
        txtDiseaseConfidence.setVisibility(View.GONE);
    }

    private void showPestConfidence() {
        txtPestConfidence.setVisibility(View.VISIBLE);
    }

    private void hidePestConfidence() {
        txtPestConfidence.setText("");
        txtPestConfidence.setVisibility(View.GONE);
    }

    private void showImageFromIntent() {
        String imageUriString = getIntent().getStringExtra("image_uri");

        if (imageUriString != null && !imageUriString.trim().isEmpty()) {
            Uri imageUri = Uri.parse(imageUriString);
            imgResult.setImageURI(imageUri);
        } else {
            Bitmap image = getIntent().getParcelableExtra("image");

            if (image != null) {
                imgResult.setImageBitmap(image);
            }
        }
    }

    private void showResultFromIntent() {
        int responseCode = getIntent().getIntExtra("responseCode", -1);
        String responseMessage = getIntent().getStringExtra("responseMessage");
        updateResultHeader(responseMessage);
        hideRawResponse();

        if (responseCode == 200 || responseCode == 201) {
            txtStatus.setVisibility(View.VISIBLE);
            hideRetryPendingUploadButton();
            if (scanMode.equals("pest")) {
                txtStatus.setText("Inference hama berhasil");
            } else {
                txtStatus.setText("Inference penyakit berhasil");
            }

            parseInferenceResult(responseMessage);

        } else if (responseCode != -1) {
            showSafeErrorResult(responseCode, responseMessage);

        } else {
            txtStatus.setVisibility(View.GONE);
            setDefaultResult();
        }
    }

    private void setupRetryPendingUploadButton() {
        if (btnRetryPendingUpload == null) {
            return;
        }

        if (!hasPendingUpload()) {
            hideRetryPendingUploadButton();
            return;
        }

        btnRetryPendingUpload.setOnClickListener(v -> retryPendingUpload());
    }

    private boolean hasPendingUpload() {
        String pendingPath = getIntent().getStringExtra("pending_image_path");
        return getIntent().getBooleanExtra("has_pending_upload", false)
                && pendingPath != null
                && !pendingPath.trim().isEmpty();
    }

    private void showRetryPendingUploadButton() {
        if (btnRetryPendingUpload != null && hasPendingUpload()) {
            btnRetryPendingUpload.setVisibility(View.VISIBLE);
        }
    }

    private void hideRetryPendingUploadButton() {
        if (btnRetryPendingUpload != null) {
            btnRetryPendingUpload.setVisibility(View.GONE);
        }
    }

    private void retryPendingUpload() {
        String pendingPath = getIntent().getStringExtra("pending_image_path");

        if (pendingPath == null || pendingPath.trim().isEmpty()) {
            hideRetryPendingUploadButton();
            return;
        }

        Intent intent = new Intent(this, CameraCaptureActivity.class);
        intent.putExtra("retry_pending_upload", true);
        intent.putExtra("pending_image_path", pendingPath);
        intent.putExtra("filename", getIntent().getStringExtra("pending_filename"));
        intent.putExtra("scan_mode", getIntent().getStringExtra("pending_scan_mode"));
        startActivity(intent);
        finish();
    }

    private void parseInferenceResult(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                setDefaultResult();
                showSafeProcessingError("Hasil dari server kosong. Silakan coba lagi.");
                return;
            }

            JSONObject json = new JSONObject(responseMessage);

            if (hasApiError(json)) {
                showSafeErrorResult(0, responseMessage);
                return;
            }

            if (scanMode.equals("pest")) {
                parsePestResult(json);
            } else {
                parseDiseaseResult(json);
            }

            hideRawResponse();

        } catch (Exception e) {
            setDefaultResult();
            showSafeProcessingError("Format hasil deteksi tidak valid. Silakan coba ulang.");
        }
    }

    private void showSafeErrorResult(int responseCode, String responseMessage) {
        txtStatus.setVisibility(View.VISIBLE);
        txtStatus.setText(getSafeErrorTitle(responseCode, responseMessage));
        setDefaultResult();
        setSafeErrorMessage(getSafeErrorMessage(responseCode, responseMessage));
        showRetryPendingUploadButton();
        hideRawResponse();
    }

    private void showSafeProcessingError(String message) {
        txtStatus.setVisibility(View.VISIBLE);
        txtStatus.setText("Hasil deteksi belum bisa ditampilkan");
        setSafeErrorMessage(message);
        showRetryPendingUploadButton();
        hideRawResponse();
    }

    private void setSafeErrorMessage(String message) {
        if (scanMode.equals("pest")) {
            showPestConfidence();
            txtPestName.setText("Status: Deteksi belum berhasil");
            txtPestConfidence.setText("Confidence: -");
            txtPestSolution.setText(message);
        } else {
            showDiseaseConfidence();
            txtDiseaseName.setText("Status Tanaman: Deteksi belum berhasil");
            txtDiseaseConfidence.setText("Confidence: -");
            txtDiseaseDescription.setText("Keterangan: " + message);
            txtDiseaseSolution.setText(message);
        }
    }

    private void hideRawResponse() {
        if (txtRawResponse != null) {
            txtRawResponse.setVisibility(View.GONE);
            txtRawResponse.setText("");
        }
    }

    private boolean hasApiError(JSONObject json) {
        if (hasNonEmptyJsonValue(json, "error") || hasNonEmptyJsonValue(json, "errors")) {
            return true;
        }

        String status = json.optString("status", "").trim().toLowerCase(Locale.US);
        if (status.equals("error") || status.equals("failed") || status.equals("failure")) {
            return true;
        }

        boolean hasInferenceResult = json.has("disease")
                || json.has("pest")
                || json.has("recommendation")
                || json.has("disease_candidates");

        return !hasInferenceResult
                && (hasNonEmptyJsonValue(json, "detail")
                || hasNonEmptyJsonValue(json, "message"));
    }

    private boolean hasNonEmptyJsonValue(JSONObject json, String key) {
        Object value = json.opt(key);
        return value != null
                && value != JSONObject.NULL
                && !String.valueOf(value).trim().isEmpty();
    }

    private String getSafeErrorTitle(int responseCode, String responseMessage) {
        String errorText = getLowercaseErrorText(responseMessage);

        if (responseCode == 401 || responseCode == 403
                || errorText.contains("token")
                || errorText.contains("auth")
                || errorText.contains("login")) {
            return "Sesi login perlu diperbarui";
        }

        if (responseCode >= 500) {
            return "Server deteksi sedang bermasalah";
        }

        if (responseCode == 408
                || errorText.contains("timeout")
                || errorText.contains("timed out")) {
            return "Koneksi terlalu lama merespons";
        }

        if (responseCode == 0
                || errorText.contains("network")
                || errorText.contains("connect")
                || errorText.contains("unreachable")) {
            return "Koneksi bermasalah";
        }

        return "Proses deteksi gagal";
    }

    private String getSafeErrorMessage(int responseCode, String responseMessage) {
        String errorText = getLowercaseErrorText(responseMessage);

        if (responseCode == 401 || responseCode == 403
                || errorText.contains("token")
                || errorText.contains("auth")
                || errorText.contains("login")) {
            return "Silakan login ulang, lalu coba deteksi lagi.";
        }

        if (responseCode >= 500) {
            return "Server belum bisa memproses gambar saat ini. Silakan coba lagi beberapa saat lagi.";
        }

        if (responseCode == 408
                || errorText.contains("timeout")
                || errorText.contains("timed out")) {
            return "Koneksi terlalu lama merespons. Periksa jaringan lalu coba lagi.";
        }

        if (responseCode == 0
                || errorText.contains("network")
                || errorText.contains("connect")
                || errorText.contains("unreachable")) {
            return "Periksa koneksi internet lalu coba lagi.";
        }

        if (responseCode >= 400 && responseCode < 500) {
            return "Permintaan belum bisa diproses. Pastikan gambar sudah sesuai, lalu coba lagi.";
        }

        return "Terjadi kendala saat memproses deteksi. Silakan coba lagi.";
    }

    private String getLowercaseErrorText(String responseMessage) {
        if (responseMessage == null || responseMessage.trim().isEmpty()) {
            return "";
        }

        return responseMessage.toLowerCase(Locale.US);
    }

    private void updateResultHeader(String responseMessage) {
        String filename = getIntent().getStringExtra("filename");

        if (filename == null || filename.trim().isEmpty()) {
            filename = getFilenameFromResponse(responseMessage);
        }

        if (filename == null || filename.trim().isEmpty()) {
            filename = "Hasil deteksi";
        }

        tvResultFileName.setText(filename);
        tvResultDate.setText(getDateFromResponse(responseMessage));
    }

    private String getFilenameFromResponse(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                return "";
            }

            JSONObject json = new JSONObject(responseMessage);
            String filename = getFirstJsonString(
                    json,
                    "filename",
                    "file_name",
                    "name_file",
                    "nameFile",
                    "name",
                    "original_filename",
                    "originalFileName"
            );

            if (!filename.trim().isEmpty()) {
                return filename;
            }

            if (json.has("data") && !json.isNull("data")) {
                JSONObject data = json.getJSONObject("data");
                filename = getFirstJsonString(
                        data,
                        "filename",
                        "file_name",
                        "name_file",
                        "nameFile",
                        "name",
                        "original_filename",
                        "originalFileName"
                );
                if (!filename.trim().isEmpty()) {
                    return filename;
                }

                JSONObject imageInfo = data.optJSONObject("image_info");
                if (imageInfo != null) {
                    filename = getFirstJsonString(
                            imageInfo,
                            "filename",
                            "file_name",
                            "name_file",
                            "nameFile",
                            "name",
                            "original_filename",
                            "originalFileName"
                    );
                    if (!filename.trim().isEmpty()) {
                        return filename;
                    }
                }
            }

            if (json.has("image") && !json.isNull("image")) {
                filename = getFirstJsonString(
                        json.getJSONObject("image"),
                        "filename",
                        "file_name",
                        "name_file",
                        "nameFile",
                        "name",
                        "original_filename",
                        "originalFileName"
                );
                if (!filename.trim().isEmpty()) {
                    return filename;
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String getDateFromResponse(String responseMessage) {
        try {
            if (responseMessage != null && !responseMessage.trim().isEmpty()) {
                JSONObject json = new JSONObject(responseMessage);
                String timestamp = getFirstJsonString(
                        json,
                        "created_at",
                        "timestamp",
                        "time",
                        "date",
                        "uploaded_at"
                );

                if (!timestamp.trim().isEmpty()) {
                    return timestamp;
                }
            }
        } catch (Exception ignored) {
        }

        SimpleDateFormat outputFormat = new SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                new Locale("id", "ID")
        );
        return outputFormat.format(new Date());
    }

    private String getFirstJsonString(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "");

            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private void parseDiseaseResult(JSONObject json) throws Exception {
        boolean isBelowThreshold = false;
        showDiseaseConfidence();
        String environmentLabel = getEnvironmentLabel(json);
        setEnvironmentText(txtDiseaseEnvironment, environmentLabel);

        if (json.has("disease") && !json.isNull("disease")) {
            JSONObject disease = json.getJSONObject("disease");

            String diseaseClass = disease.optString("class_name", "-");
            double diseaseConfidencePercent = getPercentValue(disease, "confidence_percent", "confidence");
            double thresholdPercentValue = getPercentValue(disease, "threshold_percent", "threshold");
            boolean isDetected = disease.optBoolean(
                    "is_detected",
                    diseaseConfidencePercent >= thresholdPercentValue
            );
            boolean isHealthy = isHealthyClass(diseaseClass);

            if (!isDetected) {
                isBelowThreshold = true;
                tvResultObject.setText("Tidak Terdeteksi");
                txtStatus.setText("Tidak ada yang terdeteksi");
                txtDiseaseName.setText("Nama Penyakit: -");
                txtDiseaseDescription.setText(appendEnvironmentText(
                        "Keterangan: Tidak ada yang terdeteksi.",
                        environmentLabel
                ));
                hideDiseaseConfidence();
            } else if (isHealthy) {
                txtStatus.setText("Inference berhasil, tanaman terdeteksi sehat");
                tvResultObject.setText("Tanaman Sehat");
                txtDiseaseName.setText("Status Tanaman: Sehat");
                txtDiseaseDescription.setText(appendEnvironmentText(
                        "Keterangan: Tanaman terdeteksi sehat. Tidak ditemukan indikasi gangguan pada daun.",
                        environmentLabel
                ));
            } else {
                txtStatus.setText("Inference berhasil, penyakit terdeteksi");
                tvResultObject.setText("Penyakit");
                txtDiseaseName.setText("Nama Penyakit: " + formatClassName(diseaseClass));
                txtDiseaseDescription.setText(appendEnvironmentText(
                        "Deskripsi: " + getDiseaseDescription(diseaseClass),
                        environmentLabel
                ));
            }

            if (isDetected) {
                txtDiseaseConfidence.setText(formatConfidenceWithThreshold(
                        diseaseConfidencePercent,
                        thresholdPercentValue
                ));
            }

        } else {
            tvResultObject.setText("Analisis Daun");
            txtDiseaseName.setText("Status Tanaman: -");
            txtDiseaseConfidence.setText("Confidence: -");
            txtDiseaseDescription.setText(appendEnvironmentText(
                    "Keterangan: Tidak ada data hasil analisis.",
                    environmentLabel
            ));
        }

        String diseaseSolution = getDiseaseSolutionFromJson(json);

        if (isBelowThreshold) {
            diseaseSolution = "Catatan: Hasil inference di bawah ambang deteksi, sehingga tidak ada penyakit yang ditampilkan sebagai terdeteksi.";
        } else if (diseaseSolution.trim().isEmpty()) {
            if (json.has("disease") && !json.isNull("disease")) {
                JSONObject disease = json.getJSONObject("disease");
                diseaseSolution = getDiseaseFallbackSolution(disease.optString("class_name", ""));
            }
        }

        if (diseaseSolution.trim().isEmpty()) {
            diseaseSolution = "-";
        }

        if (isBelowThreshold) {
            txtDiseaseSolution.setText(diseaseSolution);
        } else {
            txtDiseaseSolution.setText("Solusi: " + diseaseSolution);
        }
    }

    private void parsePestResult(JSONObject json) throws Exception {
        boolean hasDetectedPest = false;
        boolean hasBelowThresholdPest = false;
        String environmentLabel = getEnvironmentLabel(json);
        setEnvironmentText(txtPestEnvironment, environmentLabel);
        showPestConfidence();

        if (json.has("pest") && !json.isNull("pest")) {
            JSONArray pestArray = json.getJSONArray("pest");

            if (pestArray.length() > 0) {
                StringBuilder pestNames = new StringBuilder();
                StringBuilder pestConfidences = new StringBuilder();
                int detectedNumber = 1;

                for (int i = 0; i < pestArray.length(); i++) {
                    JSONObject pest = pestArray.getJSONObject(i);

                    String pestClass = pest.optString("class_name", "-");
                    double pestConfidencePercent = getPercentValue(pest, "confidence_percent", "confidence");
                    double pestThresholdPercent = getPercentValue(pest, "threshold_percent", "threshold");
                    boolean isDetected = pest.optBoolean(
                            "is_detected",
                            pestConfidencePercent >= pestThresholdPercent
                    );

                    if (!isDetected) {
                        hasBelowThresholdPest = true;
                        continue;
                    }

                    hasDetectedPest = true;

                    pestNames.append(detectedNumber)
                            .append(". ")
                            .append(formatClassName(pestClass));

                    pestConfidences.append(detectedNumber)
                            .append(". ")
                            .append(formatPercentValue(pestConfidencePercent));

                    detectedNumber++;

                    if (i < pestArray.length() - 1) {
                        pestNames.append("\n");
                        pestConfidences.append("\n");
                    }
                }

                if (hasDetectedPest) {
                    txtStatus.setText("Inference berhasil, hama terdeteksi");
                    if (detectedNumber == 2) {
                        txtPestName.setText("Nama Hama: " + pestNames.toString().replace("1. ", "").trim());
                        txtPestConfidence.setText("Confidence: " + pestConfidences.toString().replace("1. ", "").trim());
                    } else {
                        txtPestName.setText("Nama Hama:\n" + pestNames.toString().trim());
                        txtPestConfidence.setText("Confidence:\n" + pestConfidences.toString().trim());
                    }
                } else {
                    txtStatus.setText("Tidak ada yang terdeteksi");
                    txtPestName.setText("Nama Hama: -");
                    hidePestConfidence();
                }

            } else {
                txtPestName.setText("Nama Hama: Tidak ada hama terdeteksi");
                txtPestConfidence.setText("Confidence: -");
            }

        } else {
            txtPestName.setText("Nama Hama: Tidak ada data hama");
            txtPestConfidence.setText("Confidence: -");
        }

        String pestSolution = getPestSolutionFromJson(json);

        if (!hasDetectedPest && hasBelowThresholdPest) {
            pestSolution = "Catatan: Hasil inference di bawah ambang deteksi, sehingga tidak ada hama yang ditampilkan sebagai terdeteksi.";
        } else if (pestSolution.trim().isEmpty()) {
            pestSolution = hasDetectedPest
                    ? "Pisahkan daun yang terserang bila memungkinkan, bersihkan area tanam, dan lakukan pemantauan rutin. Gunakan pengendalian hama sesuai jenis hama yang terdeteksi."
                    : "-";
        }

        if (!hasDetectedPest && hasBelowThresholdPest) {
            txtPestSolution.setText(appendEnvironmentText(pestSolution, environmentLabel));
        } else {
            txtPestSolution.setText(appendEnvironmentText("Solusi: " + pestSolution, environmentLabel));
        }
    }

    private void setDefaultResult() {
        showDiseaseConfidence();
        txtDiseaseName.setText("Status Tanaman: -");
        txtDiseaseConfidence.setText("Confidence: -");
        setEnvironmentText(txtDiseaseEnvironment, "");
        txtDiseaseDescription.setText("Keterangan: -");
        txtDiseaseSolution.setText("Solusi: -");

        showPestConfidence();
        txtPestName.setText("Nama Hama: -");
        txtPestConfidence.setText("Confidence: -");
        setEnvironmentText(txtPestEnvironment, "");
        txtPestSolution.setText("Solusi: -");
    }

    private String getDiseaseSolutionFromJson(JSONObject json) {
        return getSolutionFromJson(
                json,
                "disease_recommendation",
                "recommendation_detail",
                "recommendation",
                "solution",
                "treatment",
                "advice"
        );
    }

    private boolean isHealthyClass(String className) {
        if (className == null) {
            return false;
        }

        String normalized = className.trim()
                .toLowerCase(Locale.US)
                .replace("_", " ")
                .replace("-", " ");

        return normalized.equals("healthy")
                || normalized.equals("sehat")
                || normalized.equals("tanaman sehat")
                || normalized.contains("healthy");
    }

    private String getPestSolutionFromJson(JSONObject json) {
        return getSolutionFromJson(
                json,
                "pest_recommendation",
                "recommendation_detail",
                "recommendation",
                "solution",
                "treatment",
                "advice"
        );
    }

    private String getSolutionFromJson(JSONObject json, String... keys) {
        if (json == null) {
            return "";
        }

        for (String key : keys) {
            Object value = json.opt(key);
            String solution = parseRecommendationValue(value);

            if (!solution.trim().isEmpty() && !isDetectionResultSummaryText(solution)) {
                return solution.trim();
            }
        }

        return "";
    }

    private String parseRecommendationValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }

        if (value instanceof String) {
            return ((String) value).trim();
        }

        if (value instanceof JSONArray) {
            return parseRecommendationArray((JSONArray) value);
        }

        if (value instanceof JSONObject) {
            return parseRecommendationObject((JSONObject) value);
        }

        return String.valueOf(value).trim();
    }

    private String parseRecommendationObject(JSONObject recommendation) {
        StringBuilder builder = new StringBuilder();

        appendFirstText(
                builder,
                recommendation,
                "description",
                "detail",
                "message",
                "solution",
                "treatment",
                "advice"
        );

        appendActions(
                builder,
                recommendation,
                "actions",
                "recommended_actions",
                "steps",
                "treatment_steps",
                "recommendations",
                "controls"
        );

        String disclaimer = getFirstText(recommendation, "disclaimer", "note", "warning");
        if (!disclaimer.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("Catatan: ").append(disclaimer.trim());
        }

        if (builder.length() > 0) {
            return builder.toString().trim();
        }

        return getFirstText(
                recommendation,
                "action",
                "text",
                "title",
                "value",
                "name"
        );
    }

    private String parseRecommendationArray(JSONArray array) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < array.length(); i++) {
            String action = parseActionValue(array.opt(i));

            if (!action.trim().isEmpty()) {
                builder.append("- ").append(action.trim()).append("\n");
            }
        }

        return builder.toString().trim();
    }

    private String parseActionValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }

        if (value instanceof JSONObject) {
            JSONObject action = (JSONObject) value;
            String actionText = getFirstText(
                    action,
                    "action",
                    "text",
                    "title",
                    "description",
                    "value",
                    "name"
            );

            if (!actionText.trim().isEmpty()) {
                return actionText;
            }

            return parseRecommendationObject(action);
        }

        if (value instanceof JSONArray) {
            return parseRecommendationArray((JSONArray) value);
        }

        return String.valueOf(value).trim();
    }

    private void appendFirstText(StringBuilder builder, JSONObject object, String... keys) {
        String text = getFirstText(object, keys);

        if (!text.trim().isEmpty() && !isDetectionResultSummaryText(text)) {
            builder.append(text.trim()).append("\n");
        }
    }

    private boolean isDetectionResultSummaryText(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text.trim().toLowerCase(Locale.US);
        return normalized.contains("terdeteksi pada citra")
                || normalized.contains("terdeteksi pada gambar")
                || normalized.contains("hama terdeteksi")
                || normalized.contains("penyakit terdeteksi")
                || normalized.contains("detected on image")
                || normalized.contains("detected in image")
                || normalized.contains("detected classes");
    }

    private void appendActions(StringBuilder builder, JSONObject object, String... keys) {
        for (String key : keys) {
            String actions = parseRecommendationValue(object.opt(key));

            if (!actions.trim().isEmpty()) {
                builder.append(actions.trim()).append("\n");
            }
        }
    }

    private String getFirstText(JSONObject object, String... keys) {
        for (String key : keys) {
            Object rawValue = object.opt(key);

            if (rawValue == null || rawValue == JSONObject.NULL
                    || rawValue instanceof JSONObject
                    || rawValue instanceof JSONArray) {
                continue;
            }

            String value = String.valueOf(rawValue);

            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private String getDiseaseDescription(String className) {
        if (className == null) {
            return "-";
        }

        switch (className) {
            case "healthy":
                return "Tanaman terdeteksi sehat. Tidak ditemukan indikasi penyakit pada daun.";

            case "fungal_leaf_spot":
                return "Terdeteksi indikasi bercak daun akibat infeksi jamur. Biasanya ditandai dengan bercak pada permukaan daun.";

            case "leaf_miner":
                return "Terdeteksi indikasi kerusakan akibat leaf miner. Biasanya terlihat seperti jalur berkelok pada daun.";

            case "powdery_mildew":
                return "Terdeteksi indikasi powdery mildew. Biasanya ditandai dengan lapisan putih seperti tepung pada daun.";

            default:
                return "Deskripsi penyakit belum tersedia untuk kelas ini.";
        }
    }

    private String getDiseaseFallbackSolution(String className) {
        if (className == null) {
            return "";
        }

        String normalizedClass = className.trim()
                .toLowerCase(Locale.US)
                .replace("-", "_")
                .replace(" ", "_");

        switch (normalizedClass) {
            case "healthy":
                return "Tanaman terdeteksi sehat. Lanjutkan pemantauan rutin, jaga kebersihan area tanam, dan pertahankan pola penyiraman serta nutrisi yang sesuai.";

            case "fungal_leaf_spot":
                return "Buang daun yang bercak parah, kurangi kelembapan berlebih, hindari penyiraman langsung ke daun, dan gunakan fungisida sesuai dosis bila gejala meluas.";

            case "leaf_miner":
                return "Pangkas daun yang banyak memiliki jalur gerekan, pasang perangkap kuning, bersihkan gulma sekitar tanaman, dan gunakan pengendalian hama sesuai anjuran bila serangan meningkat.";

            case "powdery_mildew":
                return "Tingkatkan sirkulasi udara, kurangi kelembapan pada daun, pisahkan daun yang terinfeksi, dan gunakan fungisida yang sesuai bila lapisan putih terus menyebar.";

            default:
                return "";
        }
    }

    private double getPercentValue(JSONObject object, String percentKey, String fractionKey) {
        double percentValue = object.optDouble(percentKey, -1);

        if (percentValue >= 0) {
            return percentValue;
        }

        double fractionValue = object.optDouble(fractionKey, 0);

        if (fractionValue <= 1) {
            return fractionValue * 100;
        }

        return fractionValue;
    }

    private String formatConfidenceWithThreshold(double confidencePercent, double thresholdPercent) {
        return "Confidence: " + formatPercentValue(confidencePercent);
    }

    private String getEnvironmentLabel(JSONObject json) {
        if (json == null) {
            return "";
        }

        JSONObject environment = json.optJSONObject("environment");

        if (environment == null) {
            environment = json.optJSONObject("sensor_snapshot");
        }

        if (environment == null) {
            environment = json.optJSONObject("sensor");
        }

        double temperature = getOptionalDouble(environment, json, "temperature", "temp");
        double humidity = getOptionalDouble(environment, json, "humidity", "hum");
        StringBuilder builder = new StringBuilder();

        if (!Double.isNaN(temperature)) {
            builder.append(String.format(Locale.US, "Suhu %.1f°C", temperature));
        }

        if (!Double.isNaN(humidity)) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(String.format(Locale.US, "RH %.1f%%", humidity));
        }

        return builder.toString();
    }

    private double getOptionalDouble(JSONObject primary, JSONObject fallback, String... keys) {
        for (String key : keys) {
            if (primary != null && primary.has(key) && !primary.isNull(key)) {
                double value = primary.optDouble(key, Double.NaN);
                if (!Double.isNaN(value)) {
                    return value;
                }
            }

            if (fallback != null && fallback.has(key) && !fallback.isNull(key)) {
                double value = fallback.optDouble(key, Double.NaN);
                if (!Double.isNaN(value)) {
                    return value;
                }
            }
        }

        return Double.NaN;
    }

    private String appendEnvironmentText(String text, String environmentLabel) {
        if (environmentLabel == null || environmentLabel.trim().isEmpty()) {
            return text;
        }

        return text + "\nKondisi saat deteksi: " + environmentLabel;
    }

    private void setEnvironmentText(TextView view, String environmentLabel) {
        if (view == null) {
            return;
        }

        if (environmentLabel == null || environmentLabel.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            view.setText("");
            return;
        }

        view.setVisibility(View.VISIBLE);
        view.setText("Kondisi saat deteksi: " + environmentLabel);
    }

    private String formatPercentValue(double value) {
        return String.format("%.2f%%", value);
    }

    private String formatClassName(String className) {
        if (className == null || className.trim().isEmpty()) {
            return "-";
        }

        return className
                .replace("_", " ")
                .trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (instance == this) {
            instance = null;
        }
    }

    public static void showUploadError(String message) {
        if (instance == null) return;

        instance.runOnUiThread(() -> {
            instance.showSafeErrorResult(0, message);
        });
    }

    public static void showUploadSuccess(String message) {
        if (instance == null) return;

        instance.runOnUiThread(() -> {
            instance.txtStatus.setVisibility(View.VISIBLE);
            instance.txtStatus.setText("Upload / Inference berhasil");
            instance.hideRawResponse();
        });
    }
}
