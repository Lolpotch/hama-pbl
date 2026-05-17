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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DetectionHistoryActivity extends AppCompatActivity {

    private LinearLayout detectionContainer;
    private TextView tvTotalDetection, tvHighConfidence;

    private DatabaseReference diseaseResultRef;
    private DatabaseReference pestResultRef;

    private static final int LIMIT_EACH_MODE = 100;
    private static final int MAX_RENDER_ITEMS = 100;

    private final List<DetectionHistoryItem> allDetectionItems = new ArrayList<>();

    private boolean diseaseLoaded = false;
    private boolean pestLoaded = false;

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

        pestResultRef = FirebaseDatabase.getInstance()
                .getReference("inference_result")
                .child("pest");

        showLoadingText();
        loadDetectionCounters();
        loadAllDetections();
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

        loadDiseaseDetections();
        loadPestDetections();
    }

    private void loadDetectionCounters() {
        final int[] totalCount = {0};
        final int[] highConfidenceCount = {0};
        final boolean[] diseaseDone = {false};
        final boolean[] pestDone = {false};

        diseaseResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int[] result = countDetectionsOnly(snapshot);

                totalCount[0] += result[0];
                highConfidenceCount[0] += result[1];

                diseaseDone[0] = true;

                if (diseaseDone[0] && pestDone[0]) {
                    tvTotalDetection.setText(String.valueOf(totalCount[0]));
                    tvHighConfidence.setText(String.valueOf(highConfidenceCount[0]));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                diseaseDone[0] = true;

                if (diseaseDone[0] && pestDone[0]) {
                    tvTotalDetection.setText(String.valueOf(totalCount[0]));
                    tvHighConfidence.setText(String.valueOf(highConfidenceCount[0]));
                }
            }
        });

        pestResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int[] result = countDetectionsOnly(snapshot);

                totalCount[0] += result[0];
                highConfidenceCount[0] += result[1];

                pestDone[0] = true;

                if (diseaseDone[0] && pestDone[0]) {
                    tvTotalDetection.setText(String.valueOf(totalCount[0]));
                    tvHighConfidence.setText(String.valueOf(highConfidenceCount[0]));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                pestDone[0] = true;

                if (diseaseDone[0] && pestDone[0]) {
                    tvTotalDetection.setText(String.valueOf(totalCount[0]));
                    tvHighConfidence.setText(String.valueOf(highConfidenceCount[0]));
                }
            }
        });
    }

    private int[] countDetectionsOnly(DataSnapshot snapshot) {
        int total = 0;
        int highConfidence = 0;

        for (DataSnapshot data : snapshot.getChildren()) {
            String key = data.getKey();

            if (key != null && key.equalsIgnoreCase("latest")) {
                continue;
            }

            String className = getSafeString(data.child("class_name"));
            String imageUrl = getSafeString(data.child("image_url"));
            Double confidenceValue = data.child("confidence").getValue(Double.class);

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

            if (confidencePercent >= 80) {
                highConfidence++;
            }
        }

        return new int[]{total, highConfidence};
    }

    private void loadDiseaseDetections() {
        Query query = diseaseResultRef
                .orderByChild("timestamp")
                .limitToLast(LIMIT_EACH_MODE);

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
        Query query = pestResultRef
                .orderByChild("timestamp")
                .limitToLast(LIMIT_EACH_MODE);

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

                // Pest belum ada di Firebase juga tidak masalah.
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

            String className = getSafeString(data.child("class_name"));
            String imageUrl = getSafeString(data.child("image_url"));
            String mode = getSafeString(data.child("mode"));
            String recommendation = getSafeString(data.child("recommendation"));
            String source = getSafeString(data.child("source"));
            String timestamp = getSafeString(data.child("timestamp"));
            String status = getSafeString(data.child("status"));

            Boolean handledValue = data.child("handled").getValue(Boolean.class);
            Double confidenceValue = data.child("confidence").getValue(Double.class);

            if (recommendation == null || recommendation.trim().isEmpty()) {
                recommendation = getSafeString(data.child("recommendation_detail").child("summary"));
            }

            if (recommendation == null || recommendation.trim().isEmpty()) {
                recommendation = "Belum ada rekomendasi penanganan.";
            }

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

            allDetectionItems.add(new DetectionHistoryItem(
                    alert,
                    handled,
                    mode,
                    source,
                    String.format(Locale.US, "%.2f", confidencePercent),
                    confidencePercent,
                    timestamp
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
                String t1 = o1.rawTimestamp == null ? "" : o1.rawTimestamp;
                String t2 = o2.rawTimestamp == null ? "" : o2.rawTimestamp;

                return t2.compareTo(t1);
            }
        });



        List<DetectionHistoryItem> renderList = new ArrayList<>();

        for (int i = 0; i < allDetectionItems.size(); i++) {
            if (i >= MAX_RENDER_ITEMS) {
                break;
            }

            renderList.add(allDetectionItems.get(i));
        }

        showDetectionGrid(renderList);
    }

    private void showDetectionGrid(List<DetectionHistoryItem> detectionList) {
        detectionContainer.removeAllViews();

        if (detectionList.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Belum ada riwayat deteksi.");
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

            Object summary = map.get("summary");
            if (summary != null) {
                return String.valueOf(summary);
            }

            Object detectedClass = map.get("detected_class");
            if (detectedClass != null) {
                return String.valueOf(detectedClass);
            }

            return "";
        }

        return String.valueOf(value);
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
        double confidenceValue;
        String rawTimestamp;

        DetectionHistoryItem(
                AlertPanel alert,
                boolean handled,
                String mode,
                String source,
                String confidence,
                double confidenceValue,
                String rawTimestamp
        ) {
            this.alert = alert;
            this.handled = handled;
            this.mode = mode;
            this.source = source;
            this.confidence = confidence;
            this.confidenceValue = confidenceValue;
            this.rawTimestamp = rawTimestamp;
        }
    }
}