package com.example.penyakitan;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DetectionHistoryActivity extends AppCompatActivity {

    private LinearLayout detectionContainer;
    private TextView tvTotalDetection, tvHighConfidence;

    private DatabaseReference diseaseResultRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detection_history);

        detectionContainer = findViewById(R.id.detectionContainer);
        tvTotalDetection = findViewById(R.id.tvTotalDetection);
        tvHighConfidence = findViewById(R.id.tvHighConfidence);

        diseaseResultRef = FirebaseDatabase.getInstance()
                .getReference("inference_result")
                .child("disease");

        loadAllDetections();
    }

    private void loadAllDetections() {
        Query query = diseaseResultRef.orderByChild("timestamp");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                detectionContainer.removeAllViews();

                List<DetectionHistoryItem> detectionList = new ArrayList<>();

                int total = 0;
                int highConfidence = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String className = data.child("class_name").getValue(String.class);
                    String imageUrl = data.child("image_url").getValue(String.class);
                    String mode = data.child("mode").getValue(String.class);
                    String recommendation = data.child("recommendation").getValue(String.class);
                    String source = data.child("source").getValue(String.class);
                    String timestamp = data.child("timestamp").getValue(String.class);
                    String status = data.child("status").getValue(String.class);

                    Boolean handledValue = data.child("handled").getValue(Boolean.class);
                    Double confidenceValue = data.child("confidence").getValue(Double.class);

                    boolean handled = false;

                    if (handledValue != null && handledValue) {
                        handled = true;
                    }

                    if (status != null && status.equalsIgnoreCase("handled")) {
                        handled = true;
                    }

                    if (className == null || className.trim().isEmpty()) {
                        className = "Tidak Diketahui";
                    }

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        imageUrl = "placeholder";
                    }

                    if (mode == null || mode.trim().isEmpty()) {
                        mode = "disease";
                    }

                    if (source == null || source.trim().isEmpty()) {
                        source = "kamera";
                    }

                    if (timestamp == null || timestamp.trim().isEmpty()) {
                        timestamp = "-";
                    }

                    if (recommendation == null || recommendation.trim().isEmpty()) {
                        recommendation = "Belum ada rekomendasi penanganan.";
                    }

                    double confidencePercent = 0;

                    if (confidenceValue != null) {
                        confidencePercent = confidenceValue * 100;
                    }

                    if (confidencePercent >= 80) {
                        highConfidence++;
                    }

                    total++;

                    String displayName = formatClassName(className);

                    String description =
                            "Mode: " + mode +
                                    "\nSource: " + source +
                                    "\nConfidence: " + String.format(Locale.US, "%.2f", confidencePercent) + "%";

                    AlertPanel alert = new AlertPanel(
                            source,
                            displayName,
                            imageUrl,
                            timestamp,
                            description,
                            recommendation
                    );

                    detectionList.add(new DetectionHistoryItem(
                            alert,
                            handled,
                            mode,
                            source,
                            String.format(Locale.US, "%.2f", confidencePercent)
                    ));
                }

                Collections.reverse(detectionList);

                tvTotalDetection.setText(String.valueOf(total));
                tvHighConfidence.setText(String.valueOf(highConfidence));

                showDetectionGrid(detectionList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(
                        DetectionHistoryActivity.this,
                        "Gagal mengambil data deteksi: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void showDetectionGrid(List<DetectionHistoryItem> detectionList) {
        detectionContainer.removeAllViews();

        LinearLayout currentRow = null;

        for (int i = 0; i < detectionList.size(); i++) {
            if (i % 2 == 0) {
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

            if (i % 2 == 0) {
                cardParams.setMargins(0, 0, dpToPx(6), 0);
            } else {
                cardParams.setMargins(dpToPx(6), 0, 0, 0);
            }

            view.setLayoutParams(cardParams);

            if (currentRow != null) {
                currentRow.addView(view);
            }
        }

        if (detectionList.size() % 2 != 0 && detectionContainer.getChildCount() > 0) {
            LinearLayout lastRow = (LinearLayout) detectionContainer.getChildAt(
                    detectionContainer.getChildCount() - 1
            );

            LinearLayout emptySpace = new LinearLayout(this);

            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    0,
                    1,
                    1f
            );

            emptyParams.setMargins(dpToPx(6), 0, 0, 0);
            emptySpace.setLayoutParams(emptyParams);
            lastRow.addView(emptySpace);
        }
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

        view.setSolveButtonVisible(false);
        view.setImageBadgesVisible(false);
        view.setHandledStatus(true);
        view.setHandledStatus(item.handled);

        view.setOnClickListener(v -> openDetectionDetail(item));

        return view;
    }

    private void openDetectionDetail(DetectionHistoryItem item) {
        AlertPanel alert = item.alert;

        Intent intent = new Intent(
                DetectionHistoryActivity.this,
                DetectionDetailActivity.class
        );

        intent.putExtra("image_url", alert.imageUrl);
        intent.putExtra("disease_name", alert.diseaseName);
        intent.putExtra("date", alert.date);
        intent.putExtra("description", alert.description);
        intent.putExtra("solution", alert.solution);
        intent.putExtra("handled", item.handled);
        intent.putExtra("mode", item.mode);
        intent.putExtra("source", item.source);
        intent.putExtra("confidence", item.confidence);

        startActivity(intent);
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

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class DetectionHistoryItem {
        AlertPanel alert;
        boolean handled;
        String mode;
        String source;
        String confidence;

        DetectionHistoryItem(
                AlertPanel alert,
                boolean handled,
                String mode,
                String source,
                String confidence
        ) {
            this.alert = alert;
            this.handled = handled;
            this.mode = mode;
            this.source = source;
            this.confidence = confidence;
        }
    }
}