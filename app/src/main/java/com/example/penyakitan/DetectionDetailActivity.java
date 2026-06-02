package com.example.penyakitan;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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
    private TextView tvTitle, tvDate, tvStatus;
    private TextView tvMode, tvSource, tvConfidence;
    private TextView tvSolution;
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
        tvConfidence = findViewById(R.id.tvDetailConfidence);
        progressConfidence = findViewById(R.id.progress_detail_confidence);

        tvSolution = findViewById(R.id.tvDetailSolution);
        btnClose = findViewById(R.id.btnCloseDetail);

        String imageUrl = getIntent().getStringExtra("image_url");
        String fileName = getIntent().getStringExtra("file_name");
        String diseaseName = getIntent().getStringExtra("disease_name");
        String date = getIntent().getStringExtra("date");
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

        double confidenceValue = parseConfidence(confidence);
        tvConfidence.setText(String.format(Locale.US, "%.2f%%", confidenceValue));
        progressConfidence.setProgress((int) Math.round(confidenceValue));
        setConfidenceColor(confidenceValue);

        tvSolution.setText(safeText(solution, "Belum ada rekomendasi penanganan."));

        setHandledStatus(handled);

        btnClose.setOnClickListener(v -> finish());
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

            return Double.parseDouble(value);
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

    private String getDisplayTitle(String fileName, String imageUrl, String fallback) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName;
        }

        fileName = extractFileName(imageUrl);

        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName;
        }

        return safeText(fallback, "Tidak Diketahui");
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
