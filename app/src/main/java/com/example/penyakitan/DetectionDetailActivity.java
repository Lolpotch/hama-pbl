package com.example.penyakitan;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.Locale;

public class DetectionDetailActivity extends AppCompatActivity {

    private ImageView imgDetail;
    private TextView tvTitle, tvDate, tvStatus;
    private TextView tvMode, tvSource, tvConfidence;
    private TextView tvSolution;
    private ProgressBar progressConfidence;
    private TextView btnClose;

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

        tvTitle.setText(safeText(diseaseName, "Tidak Diketahui"));
        tvDate.setText(safeText(date, "-"));
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
}