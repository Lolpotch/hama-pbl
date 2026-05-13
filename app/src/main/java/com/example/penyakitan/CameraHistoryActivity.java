package com.example.penyakitan;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

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

public class CameraHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private CameraAdapter adapter;

    private TextView tvTotalHistory, tvTodayHistory, tvAlertHistory;

    private final List<CameraImage> allImages = new ArrayList<>();

    private DatabaseReference cameraCapturesRef;

    private String labelFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_history);

        labelFilter = getIntent().getStringExtra("label_filter");

        if (labelFilter == null) {
            labelFilter = "";
        }

        tvTotalHistory = findViewById(R.id.tvTotalHistory);
        tvTodayHistory = findViewById(R.id.tvTodayHistory);
        tvAlertHistory = findViewById(R.id.tvAlertHistory);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        adapter = new CameraAdapter(allImages);
        recyclerView.setAdapter(adapter);

        cameraCapturesRef = FirebaseDatabase.getInstance()
                .getReference("camera_captures");

        setInitialSummary();

        loadImagesFromFirebase();
    }

    private void setInitialSummary() {
        tvTotalHistory.setText("0");
        tvTodayHistory.setText("0");
        tvAlertHistory.setText("0");
    }

    private void loadImagesFromFirebase() {
        Query query = cameraCapturesRef.orderByChild("uploaded_at");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                allImages.clear();

                int todayCount = 0;
                int alertCount = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String filename = data.child("filename").getValue(String.class);
                    String imageUrl = data.child("image").getValue(String.class);
                    String label = data.child("label").getValue(String.class);
                    String time = data.child("time").getValue(String.class);
                    Long uploadedAt = data.child("uploaded_at").getValue(Long.class);

                    if (label == null) {
                        label = "";
                    }

                    if (!labelFilter.trim().isEmpty() &&
                            !label.equalsIgnoreCase(labelFilter)) {
                        continue;
                    }

                    if (filename == null || filename.trim().isEmpty()) {
                        filename = "Foto Kamera";
                    }

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (time == null || time.trim().isEmpty()) {
                        time = "-";
                    }

                    String source = data.child("source").getValue(String.class);

                    if (source == null || source.trim().isEmpty()) {
                        if (label.equalsIgnoreCase("HP")) {
                            source = "HP";
                        } else {
                            source = "CCTV";
                        }
                    }

                    allImages.add(
                            new CameraImage(
                                    filename,
                                    imageUrl,
                                    time,
                                    label,
                                    source
                            )
                    );

                    if (isToday(uploadedAt)) {
                        todayCount++;
                    }

                    if (isDetectedLabel(label)) {
                        alertCount++;
                    }
                }

                Collections.reverse(allImages);

                adapter.notifyDataSetChanged();

                tvTotalHistory.setText(String.valueOf(allImages.size()));
                tvTodayHistory.setText(String.valueOf(todayCount));
                tvAlertHistory.setText(String.valueOf(alertCount));

                Log.d("FIREBASE_HISTORY", "Total data: " + allImages.size());
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

    private boolean isDetectedLabel(String label) {
        if (label == null) {
            return false;
        }

        String lowerLabel = label.toLowerCase();

        return lowerLabel.contains("hama") ||
                lowerLabel.contains("penyakit") ||
                lowerLabel.contains("pest") ||
                lowerLabel.contains("disease") ||
                lowerLabel.contains("detected") ||
                lowerLabel.contains("alert");
    }
}