package com.example.penyakitan;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlertView extends LinearLayout {

    private ImageView imgAlert;
    private TextView tvAlertType, tvAlertSource;
    private TextView tvDiseaseName, tvDate;
    private TextView tvHandledStatus;
    private TextView tvConfidence;
    private ProgressBar progressConfidence;
    private TextView tvDescription, tvSolution;
    private Button btnSolve;

    private Runnable solveClickListener;

    private boolean showModeSourceInDescription = false;

    public AlertView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.item_alert_panel, this, true);

        imgAlert = findViewById(R.id.imgAlert);
        tvAlertType = findViewById(R.id.tvAlertType);
        tvAlertSource = findViewById(R.id.tvAlertSource);
        tvDiseaseName = findViewById(R.id.tvDiseaseName);
        tvDate = findViewById(R.id.tvDate);
        tvHandledStatus = findViewById(R.id.tvHandledStatus);
        tvConfidence = findViewById(R.id.tvConfidence);
        progressConfidence = findViewById(R.id.progressConfidence);
        tvDescription = findViewById(R.id.tvDescription);
        tvSolution = findViewById(R.id.tvSolution);
        btnSolve = findViewById(R.id.btnSolve);

        setClickable(true);
        setFocusable(true);

        btnSolve.setOnClickListener(v -> {
            if (solveClickListener != null) {
                solveClickListener.run();
            }
        });
    }

    public void setShowModeSourceInDescription(boolean show) {
        this.showModeSourceInDescription = show;
    }

    public void setData(
            String imageUrl,
            String date,
            String diseaseName,
            String description,
            String solution
    ) {
        if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrl.equals("placeholder")) {
            Glide.with(getContext())
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.plant)
                    .error(R.drawable.plant)
                    .into(imgAlert);
        } else {
            imgAlert.setImageResource(R.drawable.plant);
        }

        tvDiseaseName.setText(safeText(diseaseName, "Tidak Diketahui"));
        tvDate.setText(safeText(date, "-"));
        tvSolution.setText(safeText(solution, "Belum ada rekomendasi penanganan."));

        String mode = extractValue(description, "Mode");
        String source = extractValue(description, "Source");
        double confidence = extractConfidence(description);

        if (mode.isEmpty()) {
            mode = "disease";
        }

        if (source.isEmpty()) {
            source = "kamera";
        }

        tvAlertType.setText(formatBadgeText(mode));
        tvAlertSource.setText(formatBadgeText(source));

        String cleanedDescription = cleanDescription(description);

        if (cleanedDescription.trim().isEmpty()) {
            tvDescription.setVisibility(View.GONE);
        } else {
            tvDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(cleanedDescription);
        }

        setConfidence(confidence);
    }

    private void setConfidence(double confidence) {
        if (confidence < 0) {
            confidence = 0;
        }

        if (confidence > 100) {
            confidence = 100;
        }

        int progress = (int) Math.round(confidence);

        tvConfidence.setText(String.format(Locale.US, "%.2f%%", confidence));
        progressConfidence.setProgress(progress);

        if (confidence >= 80) {
            tvConfidence.setTextColor(Color.parseColor("#0B7A2A"));
            progressConfidence.setProgressTintList(
                    ColorStateList.valueOf(Color.parseColor("#0B7A2A"))
            );
        } else if (confidence >= 60) {
            tvConfidence.setTextColor(Color.parseColor("#C4320A"));
            progressConfidence.setProgressTintList(
                    ColorStateList.valueOf(Color.parseColor("#F79009"))
            );
        } else {
            tvConfidence.setTextColor(Color.parseColor("#D92D20"));
            progressConfidence.setProgressTintList(
                    ColorStateList.valueOf(Color.parseColor("#D92D20"))
            );
        }
    }

    public void setOnSolveClick(Runnable listener) {
        this.solveClickListener = listener;
    }

    public void setSolveButtonVisible(boolean visible) {
        if (btnSolve != null) {
            btnSolve.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setImageBadgesVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;

        if (tvAlertType != null) {
            tvAlertType.setVisibility(visibility);
        }

        if (tvAlertSource != null) {
            tvAlertSource.setVisibility(visibility);
        }
    }

    public void setHandledStatusVisible(boolean visible) {
        if (tvHandledStatus != null) {
            tvHandledStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setHandledStatus(boolean handled) {
        if (tvHandledStatus == null) {
            return;
        }

        tvHandledStatus.setVisibility(View.VISIBLE);

        if (handled) {
            tvHandledStatus.setText("Sudah Ditangani");
            tvHandledStatus.setTextColor(Color.parseColor("#667085"));
            tvHandledStatus.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#EEF0F2"))
            );
        } else {
            tvHandledStatus.setText("Belum Ditangani");
            tvHandledStatus.setTextColor(Color.parseColor("#D92D20"));
            tvHandledStatus.setBackgroundTintList(
                    ColorStateList.valueOf(Color.parseColor("#FEE4E2"))
            );
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value;
    }

    private String cleanDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "";
        }

        String[] lines = description.split("\n");
        StringBuilder builder = new StringBuilder();

        for (String line : lines) {
            String lower = line.toLowerCase();

            if (lower.startsWith("confidence")) {
                continue;
            }

            if (!showModeSourceInDescription) {
                if (lower.startsWith("mode")) {
                    continue;
                }

                if (lower.startsWith("source")) {
                    continue;
                }
            }

            builder.append(line).append("\n");
        }

        return builder.toString().trim();
    }

    private String extractValue(String description, String key) {
        if (description == null) {
            return "";
        }

        String[] lines = description.split("\n");

        for (String line : lines) {
            if (line.toLowerCase().startsWith(key.toLowerCase() + ":")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }

        return "";
    }

    private double extractConfidence(String description) {
        if (description == null) {
            return 0;
        }

        Pattern pattern = Pattern.compile("Confidence:\\s*([0-9]+(?:\\.[0-9]+)?)%");
        Matcher matcher = pattern.matcher(description);

        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (Exception ignored) {
                return 0;
            }
        }

        return 0;
    }

    private String formatBadgeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        String cleaned = value.trim();

        if (cleaned.length() == 1) {
            return cleaned.toUpperCase();
        }

        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1).toLowerCase();
    }
}