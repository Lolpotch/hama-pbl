package com.example.penyakitan;

import android.graphics.Bitmap;
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
    private TextView tvResultFileName;
    private TextView tvResultDate;
    private TextView tvResultObject;
    private LinearLayout cardDiseaseResult;
    private LinearLayout cardPestResult;
    private TextView txtDiseaseName;
    private TextView txtDiseaseConfidence;
    private TextView txtDiseaseDescription;
    private TextView txtDiseaseSolution;

    private TextView txtPestName;
    private TextView txtPestConfidence;
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
        tvResultFileName = findViewById(R.id.tvResultFileName);
        tvResultDate = findViewById(R.id.tvResultDate);
        tvResultObject = findViewById(R.id.tvResultObject);

        cardDiseaseResult = findViewById(R.id.cardDiseaseResult);
        cardPestResult = findViewById(R.id.cardPestResult);

        txtDiseaseName = findViewById(R.id.txtDiseaseName);
        txtDiseaseConfidence = findViewById(R.id.txtDiseaseConfidence);
        txtDiseaseDescription = findViewById(R.id.txtDiseaseDescription);
        txtDiseaseSolution = findViewById(R.id.txtDiseaseSolution);

        txtPestName = findViewById(R.id.txtPestName);
        txtPestConfidence = findViewById(R.id.txtPestConfidence);
        txtPestSolution = findViewById(R.id.txtPestSolution);

        txtRawResponse = findViewById(R.id.txtRawResponse);

        readScanModeFromIntent();
        applyResultModeVisibility();
        updateResultHeader(null);
        showImageFromIntent();
        showResultFromIntent();

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
            tvResultObject.setText("Penyakit");
        }
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

        if (responseCode == 200 || responseCode == 201) {
            txtStatus.setVisibility(View.VISIBLE);
            if (scanMode.equals("pest")) {
                txtStatus.setText("Inference hama berhasil");
            } else {
                txtStatus.setText("Inference penyakit berhasil");
            }

            parseInferenceResult(responseMessage);

        } else if (responseCode != -1) {
            txtStatus.setVisibility(View.VISIBLE);
            txtStatus.setText("Upload / Inference gagal");

            setDefaultResult();

            if (responseMessage != null && !responseMessage.trim().isEmpty()) {
                txtRawResponse.setVisibility(View.VISIBLE);
                txtRawResponse.setText(responseMessage);
            }

        } else {
            txtStatus.setVisibility(View.GONE);
            setDefaultResult();
        }
    }

    private void parseInferenceResult(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                txtStatus.setText("Inference berhasil, tetapi response kosong");
                setDefaultResult();
                return;
            }

            JSONObject json = new JSONObject(responseMessage);

            if (scanMode.equals("pest")) {
                parsePestResult(json);
            } else {
                parseDiseaseResult(json);
            }

            // Raw response disembunyikan kalau parsing berhasil
            txtRawResponse.setVisibility(View.GONE);

        } catch (Exception e) {
            txtStatus.setText("Inference berhasil, tetapi gagal membaca JSON");

            setDefaultResult();

            txtRawResponse.setVisibility(View.VISIBLE);
            txtRawResponse.setText(
                    "Error: " + e.getMessage() + "\n\nRaw Response:\n" + responseMessage
            );
        }
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
        if (json.has("disease") && !json.isNull("disease")) {
            JSONObject disease = json.getJSONObject("disease");

            String diseaseClass = disease.optString("class_name", "-");
            double diseaseConfidence = disease.optDouble("confidence", 0);
            boolean isDetected = disease.optBoolean("is_detected", diseaseConfidence >= 0.60);
            int thresholdPercent = disease.optInt("threshold_percent", 60);

            if (!isDetected) {
                txtStatus.setText("Confidence di bawah " + thresholdPercent + "%, hasil belum valid");
            }

            txtDiseaseName.setText("Nama Penyakit: " + formatClassName(diseaseClass));
            txtDiseaseConfidence.setText("Confidence: " + formatPercent(diseaseConfidence));
            txtDiseaseDescription.setText("Deskripsi: " + getDiseaseDescription(diseaseClass));

        } else {
            txtDiseaseName.setText("Nama Penyakit: -");
            txtDiseaseConfidence.setText("Confidence: -");
            txtDiseaseDescription.setText("Deskripsi: Tidak ada data penyakit.");
        }

        String diseaseSolution = getDiseaseSolutionFromJson(json);

        if (diseaseSolution.trim().isEmpty()) {
            diseaseSolution = "-";
        }

        txtDiseaseSolution.setText("Solusi: " + diseaseSolution);
    }

    private void parsePestResult(JSONObject json) throws Exception {
        if (json.has("pest") && !json.isNull("pest")) {
            JSONArray pestArray = json.getJSONArray("pest");

            if (pestArray.length() > 0) {
                StringBuilder pestNames = new StringBuilder();
                StringBuilder pestConfidences = new StringBuilder();

                for (int i = 0; i < pestArray.length(); i++) {
                    JSONObject pest = pestArray.getJSONObject(i);

                    String pestClass = pest.optString("class_name", "-");
                    double pestConfidence = pest.optDouble("confidence", 0);

                    pestNames.append(i + 1)
                            .append(". ")
                            .append(formatClassName(pestClass));

                    pestConfidences.append(i + 1)
                            .append(". ")
                            .append(formatPercent(pestConfidence));

                    if (i < pestArray.length() - 1) {
                        pestNames.append("\n");
                        pestConfidences.append("\n");
                    }
                }

                txtPestName.setText("Nama Hama:\n" + pestNames);
                txtPestConfidence.setText("Confidence:\n" + pestConfidences);

            } else {
                txtPestName.setText("Nama Hama: Tidak ada hama terdeteksi");
                txtPestConfidence.setText("Confidence: -");
            }

        } else {
            txtPestName.setText("Nama Hama: Tidak ada data hama");
            txtPestConfidence.setText("Confidence: -");
        }

        String pestSolution = getPestSolutionFromJson(json);

        if (pestSolution.trim().isEmpty()) {
            pestSolution = "-";
        }

        txtPestSolution.setText("Solusi: " + pestSolution);
    }

    private void setDefaultResult() {
        txtDiseaseName.setText("Nama Penyakit: -");
        txtDiseaseConfidence.setText("Confidence: -");
        txtDiseaseDescription.setText("Deskripsi: -");
        txtDiseaseSolution.setText("Solusi: -");

        txtPestName.setText("Nama Hama: -");
        txtPestConfidence.setText("Confidence: -");
        txtPestSolution.setText("Solusi: -");
    }

    private String getDiseaseSolutionFromJson(JSONObject json) {
        try {
            StringBuilder solution = new StringBuilder();

            if (json.has("disease_recommendation") && !json.isNull("disease_recommendation")) {
                JSONObject recommendation = json.getJSONObject("disease_recommendation");

                String summary = recommendation.optString("summary", "");
                if (!summary.trim().isEmpty()) {
                    solution.append(summary).append("\n");
                }

                if (recommendation.has("recommended_actions")) {
                    JSONArray actions = recommendation.getJSONArray("recommended_actions");

                    for (int i = 0; i < actions.length(); i++) {
                        solution.append("- ")
                                .append(actions.optString(i))
                                .append("\n");
                    }
                }

                return solution.toString().trim();
            }

            if (json.has("recommendation") && !json.isNull("recommendation")) {
                Object recommendationObj = json.get("recommendation");

                if (recommendationObj instanceof JSONObject) {
                    JSONObject recommendation = json.getJSONObject("recommendation");

                    String summary = recommendation.optString("summary", "");
                    if (!summary.trim().isEmpty()) {
                        solution.append(summary).append("\n");
                    }

                    if (recommendation.has("recommended_actions")) {
                        JSONArray actions = recommendation.getJSONArray("recommended_actions");

                        for (int i = 0; i < actions.length(); i++) {
                            solution.append("- ")
                                    .append(actions.optString(i))
                                    .append("\n");
                        }
                    }

                    return solution.toString().trim();

                } else {
                    return json.optString("recommendation", "");
                }
            }

        } catch (Exception ignored) {
        }

        return "";
    }

    private String getPestSolutionFromJson(JSONObject json) {
        try {
            StringBuilder solution = new StringBuilder();

            if (json.has("pest_recommendation") && !json.isNull("pest_recommendation")) {
                JSONObject recommendation = json.getJSONObject("pest_recommendation");

                String summary = recommendation.optString("summary", "");
                if (!summary.trim().isEmpty()) {
                    solution.append(summary).append("\n");
                }

                if (recommendation.has("recommended_actions")) {
                    JSONArray actions = recommendation.getJSONArray("recommended_actions");

                    for (int i = 0; i < actions.length(); i++) {
                        solution.append("- ")
                                .append(actions.optString(i))
                                .append("\n");
                    }
                }

                return solution.toString().trim();
            }

        } catch (Exception ignored) {
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

    private String formatPercent(double value) {
        return String.format("%.2f%%", value * 100);
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
            instance.txtStatus.setVisibility(View.VISIBLE);
            instance.txtStatus.setText("Upload / Inference gagal");

            instance.txtRawResponse.setVisibility(View.VISIBLE);
            instance.txtRawResponse.setText(message);
        });
    }

    public static void showUploadSuccess(String message) {
        if (instance == null) return;

        instance.runOnUiThread(() -> {
            instance.txtStatus.setVisibility(View.VISIBLE);
            instance.txtStatus.setText("Upload / Inference berhasil");

            instance.txtRawResponse.setVisibility(View.VISIBLE);
            instance.txtRawResponse.setText(message);
        });
    }
}
