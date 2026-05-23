package com.example.penyakitan;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CameraHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CameraAdapter adapter;

    private ImageView btnBackHistory;
    private TextView tvTotalHistory, tvTodayHistory;

    private final List<CameraImage> allImages = new ArrayList<>();

    private DatabaseReference cameraCapturesRef;

    private String labelFilter = "";
    private String sourceFilter = "all";

    private static final int DISPLAY_LIMIT = 300;
    private static final String DATABASE_URL =
            "https://lokasighthama-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_history);

        labelFilter = getIntent().getStringExtra("label_filter");
        sourceFilter = getIntent().getStringExtra("source_filter");

        if (labelFilter == null) {
            labelFilter = "";
        }

        if (sourceFilter == null || sourceFilter.trim().isEmpty()) {
            sourceFilter = "all";
        }

        labelFilter = labelFilter.trim();
        sourceFilter = sourceFilter.trim().toLowerCase(Locale.US);

        btnBackHistory = findViewById(R.id.btnBackHistory);
        tvTotalHistory = findViewById(R.id.tvTotalHistory);
        tvTodayHistory = findViewById(R.id.tvTodayHistory);

        btnBackHistory.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        adapter = new CameraAdapter(allImages);
        recyclerView.setAdapter(adapter);

        cameraCapturesRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("camera_captures");

        setInitialSummary();

        loadRealCountsFromFirebase();
        loadImagesFromFirebase();
    }

    private void setInitialSummary() {
        tvTotalHistory.setText("0");
        tvTodayHistory.setText("0");
    }

    private void loadRealCountsFromFirebase() {
        Query query = cameraCapturesRef.orderByChild("uploaded_at");

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int totalCount = 0;
                int todayCount = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String filename = getSafeString(data.child("filename"));
                    String imageUrl = getSafeString(data.child("image"));
                    String label = getSafeString(data.child("label"));
                    String source = getSafeString(data.child("source"));
                    Long uploadedAt = data.child("uploaded_at").getValue(Long.class);

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (!labelFilter.trim().isEmpty()
                            && !label.equalsIgnoreCase(labelFilter)) {
                        continue;
                    }

                    if (!isAllowedBySourceFilter(label, source, filename)) {
                        continue;
                    }

                    totalCount++;

                    if (isToday(uploadedAt)) {
                        todayCount++;
                    }
                }

                tvTotalHistory.setText(String.valueOf(totalCount));
                tvTodayHistory.setText(String.valueOf(todayCount));

                Log.d("FIREBASE_HISTORY_COUNT", "Total real: " + totalCount);
                Log.d("FIREBASE_HISTORY_COUNT", "Today real: " + todayCount);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(
                        CameraHistoryActivity.this,
                        "Gagal menghitung total history: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void loadImagesFromFirebase() {
        Query query = cameraCapturesRef
                .orderByChild("uploaded_at");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                allImages.clear();

                List<CameraImage> filteredImages = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    String filename = getSafeString(data.child("filename"));
                    String imageUrl = getSafeString(data.child("image"));
                    String label = getSafeString(data.child("label"));
                    String source = getSafeString(data.child("source"));
                    String time = getSafeString(data.child("time"));

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (filename == null || filename.trim().isEmpty()) {
                        filename = "Foto Kamera";
                    }

                    if (label == null) {
                        label = "";
                    }

                    if (time == null || time.trim().isEmpty()) {
                        time = "-";
                    }

                    if (source == null || source.trim().isEmpty()) {
                        if (isMobileSource(label, source, filename)) {
                            source = "Mobile";
                        } else {
                            source = "CCTV";
                        }
                    }

                    if (!labelFilter.trim().isEmpty()
                            && !label.equalsIgnoreCase(labelFilter)) {
                        continue;
                    }

                    if (!isAllowedBySourceFilter(label, source, filename)) {
                        continue;
                    }

                    filteredImages.add(
                            new CameraImage(
                                    filename,
                                    imageUrl,
                                    time,
                                    normalizeLabel(label),
                                    normalizeSource(source, label, filename)
                            )
                    );
                }

                Collections.reverse(filteredImages);

                int limit = Math.min(filteredImages.size(), DISPLAY_LIMIT);

                for (int i = 0; i < limit; i++) {
                    allImages.add(filteredImages.get(i));
                }

                adapter.notifyDataSetChanged();

                Log.d("FIREBASE_HISTORY", "Data sesuai filter: " + filteredImages.size());
                Log.d("FIREBASE_HISTORY", "Data yang ditampilkan: " + allImages.size());
                Log.d("FIREBASE_HISTORY", "Source filter: " + sourceFilter);
                Log.d("FIREBASE_HISTORY", "Label filter: " + labelFilter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(
                        CameraHistoryActivity.this,
                        "Gagal mengambil history: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private boolean isAllowedBySourceFilter(String label, String source, String filename) {
        if (sourceFilter == null || sourceFilter.equals("all")) {
            return true;
        }

        boolean isMobile = isMobileSource(label, source, filename);

        if (sourceFilter.equals("mobile")) {
            return isMobile;
        }

        if (sourceFilter.equals("cctv")) {
            return !isMobile;
        }

        return true;
    }

    private boolean isMobileSource(String label, String source, String filename) {
        String labelLower = label == null ? "" : label.toLowerCase(Locale.US);
        String sourceLower = source == null ? "" : source.toLowerCase(Locale.US);
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.US);

        return labelLower.contains("mobile")
                || labelLower.contains("hp")
                || sourceLower.contains("mobile")
                || sourceLower.contains("hp")
                || filenameLower.contains("mobile")
                || filenameLower.contains("hp_")
                || filenameLower.contains("_hp")
                || filenameLower.startsWith("hp");
    }

    private String normalizeSource(String source, String label, String filename) {
        if (isMobileSource(label, source, filename)) {
            return "Mobile";
        }

        return "CCTV";
    }

    private String normalizeLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "-";
        }

        if (label.equalsIgnoreCase("HP")) {
            return "Mobile";
        }

        if (label.equalsIgnoreCase("mobile")) {
            return "Mobile";
        }

        if (label.equalsIgnoreCase("A")) {
            return "Depan";
        }

        if (label.equalsIgnoreCase("B")) {
            return "Belakang";
        }

        return label;
    }

    private boolean isToday(Long uploadedAt) {
        try {
            if (uploadedAt == null) {
                return false;
            }

            Date uploadedDate = new Date(uploadedAt * 1000);

            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
            );

            String uploadedDay = dateFormat.format(uploadedDate);
            String today = dateFormat.format(new Date());

            return uploadedDay.equals(today);

        } catch (Exception e) {
            return false;
        }
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

            Object image = map.get("image");
            if (image != null) {
                return String.valueOf(image);
            }

            Object imageUrl = map.get("image_url");
            if (imageUrl != null) {
                return String.valueOf(imageUrl);
            }

            return "";
        }

        return String.valueOf(value);
    }
}
