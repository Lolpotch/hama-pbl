package com.example.penyakitan;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DetectionDetailActivity extends AppCompatActivity {

    private ImageView imgDetail;
    private View environmentDivider;
    private TextView tvTitle, tvDate, tvStatus;
    private TextView tvMode, tvSource, tvDetectedNameLabel, tvDetectedName, tvConfidence;
    private TextView tvEnvironmentLabel;
    private TextView tvEnvironment, tvSolution;
    private ProgressBar progressConfidence;
    private ImageView btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection_detail);

        imgDetail = findViewById(R.id.imgDetail);
        tvTitle = findViewById(R.id.tvDetailTitle);
        tvDate = findViewById(R.id.tvDetailDate);
        tvStatus = findViewById(R.id.tvDetailStatus);

        tvMode = findViewById(R.id.tvDetailMode);
        tvSource = findViewById(R.id.tvDetailSource);
        tvDetectedNameLabel = findViewById(R.id.tvDetailDetectedNameLabel);
        tvDetectedName = findViewById(R.id.tvDetailDetectedName);
        tvConfidence = findViewById(R.id.tvDetailConfidence);
        environmentDivider = findViewById(R.id.viewDetailEnvironmentDivider);
        tvEnvironmentLabel = findViewById(R.id.tvDetailEnvironmentLabel);
        tvEnvironment = findViewById(R.id.tvDetailEnvironment);
        progressConfidence = findViewById(R.id.progress_detail_confidence);

        tvSolution = findViewById(R.id.tvDetailSolution);
        btnClose = findViewById(R.id.btnCloseDetail);

        String imageUrl = getIntent().getStringExtra("image_url");
        String fileName = getIntent().getStringExtra("file_name");
        String diseaseName = getIntent().getStringExtra("disease_name");
        String date = getIntent().getStringExtra("date");
        String description = getIntent().getStringExtra("description");
        String environmentLabel = getIntent().getStringExtra("environment_label");
        String solution = getIntent().getStringExtra("solution");
        boolean handled = getIntent().getBooleanExtra("handled", false);
        String mode = getIntent().getStringExtra("mode");
        String source = getIntent().getStringExtra("source");
        String confidence = getIntent().getStringExtra("confidence");

        if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrl.equals("placeholder")) {
            Glide.with(this)
                    .load(imageUrl)
                    .fitCenter()
                    .placeholder(R.drawable.plant)
                    .error(R.drawable.plant)
                    .into(imgDetail);
        } else {
            imgDetail.setImageResource(R.drawable.plant);
        }

        tvTitle.setText(getDisplayTitle(fileName, imageUrl, diseaseName));
        tvDate.setText(formatDisplayTime(date));
        tvMode.setText(formatLabel(safeText(mode, "-")));
        tvSource.setText(formatLabel(safeText(source, "-")));
        tvDetectedNameLabel.setText(getDetectedNameLabel(mode, diseaseName));
        tvDetectedName.setText(getDetectedNameText(mode, diseaseName));

        double confidenceValue = parseConfidence(confidence);
        tvConfidence.setText(String.format(Locale.US, "%.2f%%", confidenceValue));
        progressConfidence.setProgress((int) Math.round(confidenceValue));
        setConfidenceColor(confidenceValue);
        setEnvironmentText(environmentLabel, description);

        tvSolution.setText(safeText(solution, "Belum ada rekomendasi penanganan."));

        setHandledStatus(handled);

        btnClose.setOnClickListener(v -> finish());
    }

    private void setEnvironmentText(String environmentLabel, String description) {
        if (tvEnvironment == null) {
            return;
        }

        String environment = safeText(environmentLabel, "");

        if (environment.trim().isEmpty()) {
            environment = extractDescriptionValue(description, "Kondisi saat deteksi");
        }

        if (environment.trim().isEmpty()) {
            if (environmentDivider != null) environmentDivider.setVisibility(View.GONE);
            if (tvEnvironmentLabel != null) tvEnvironmentLabel.setVisibility(View.GONE);
            tvEnvironment.setVisibility(View.GONE);
            return;
        }

        if (environmentDivider != null) environmentDivider.setVisibility(View.VISIBLE);
        if (tvEnvironmentLabel != null) tvEnvironmentLabel.setVisibility(View.VISIBLE);
        tvEnvironment.setVisibility(View.VISIBLE);
        tvEnvironment.setText(environment);
    }

    private String extractDescriptionValue(String description, String key) {
        if (description == null || description.trim().isEmpty()) {
            return "";
        }

        String[] lines = description.split("\n");

        for (String line : lines) {
            if (line.toLowerCase(Locale.US).startsWith(key.toLowerCase(Locale.US) + ":")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }

        return "";
    }

    private void setHandledStatus(boolean handled) {
        if (handled) {
            tvStatus.setText("Sudah Ditangani");
            tvStatus.setTextColor(Color.parseColor("#667085"));
            tvStatus.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#EEF0F2"))
            );
        } else {
            tvStatus.setText("Belum Ditangani");
            tvStatus.setTextColor(Color.parseColor("#D92D20"));
            tvStatus.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FEE4E2"))
            );
        }
    }

    private void setConfidenceColor(double confidence) {
        int color;

        if (confidence >= 80) {
            color = Color.parseColor("#0B7A2A");
        } else if (confidence >= 60) {
            color = Color.parseColor("#F79009");
        } else {
            color = Color.parseColor("#D92D20");
        }

        tvConfidence.setTextColor(color);
        progressConfidence.setProgressTintList(ColorStateList.valueOf(color));
    }

    private double parseConfidence(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return 0;
            }

            double confidence = Double.parseDouble(value);
            return confidence <= 1 ? confidence * 100 : confidence;
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatLabel(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) {
            return "-";
        }

        String cleaned = value.trim();

        if (cleaned.length() == 1) {
            return cleaned.toUpperCase();
        }

        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1).toLowerCase();
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value;
    }

    private String getDetectedNameLabel(String mode, String detectedName) {
        if (mode != null && mode.equalsIgnoreCase("pest")) {
            return "Nama Hama";
        }

        if (isHealthyDetection(detectedName)) {
            return "Status Tanaman";
        }

        return "Nama Penyakit";
    }

    private String getDetectedNameText(String mode, String detectedName) {
        if (mode != null && mode.equalsIgnoreCase("disease") && isHealthyDetection(detectedName)) {
            return "Sehat";
        }

        return formatDetectionName(safeText(detectedName, "Tidak Diketahui"));
    }

    private boolean isHealthyDetection(String detectedName) {
        if (detectedName == null) {
            return false;
        }

        String normalized = detectedName.trim()
                .toLowerCase(Locale.US)
                .replace("_", " ")
                .replace("-", " ");

        return normalized.equals("healthy")
                || normalized.equals("sehat")
                || normalized.equals("tanaman sehat")
                || normalized.contains("healthy");
    }

    private String formatDetectionName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Tidak Diketahui";
        }

        return value.trim().replace("_", " ");
    }

    private String getDisplayTitle(String fileName, String imageUrl, String fallback) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName;
        }

        fileName = extractFileName(imageUrl);

        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName;
        }

        return formatDetectionName(safeText(fallback, "Tidak Diketahui"));
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

    private String formatDisplayTime(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) {
            return "-";
        }

        String text = value.trim();
        Date date = parseMillisDate(text);

        if (date != null) {
            return formatOutputDate(date);
        }

        date = parseIsoDate(text);

        if (date == null) {
            date = parseDate(text, "yyyyMMdd_HHmmss", TimeZone.getTimeZone("UTC"));
        }

        if (date == null) {
            date = parseDate(text, "yyyy-MM-dd HH:mm:ss", TimeZone.getDefault());
        }

        if (date == null) {
            return text;
        }

        return formatOutputDate(date);
    }

    private String formatOutputDate(Date date) {
        SimpleDateFormat outputFormat = new SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                new Locale("id", "ID")
        );
        outputFormat.setTimeZone(TimeZone.getDefault());

        return outputFormat.format(date);
    }

    private Date parseMillisDate(String text) {
        try {
            long rawValue = Long.parseLong(text);

            if (rawValue <= 0) {
                return null;
            }

            if (rawValue < 100000000000L) {
                rawValue *= 1000L;
            }

            return new Date(rawValue);
        } catch (Exception e) {
            return null;
        }
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
}
