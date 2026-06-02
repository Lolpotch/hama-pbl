package com.example.penyakitan;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
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
    private GridLayoutManager layoutManager;

    private ImageView btnBackHistory;
    private TextView tvTotalHistory, tvTodayHistory;
    private TextView btnLoadAllCameraHistory;

    private final List<CameraImage> allImages = new ArrayList<>();

    private DatabaseReference cameraCapturesRef;
    private DatabaseReference cameraSummaryRef;

    private String labelFilter = "";
    private String sourceFilter = "all";

    private static final int INITIAL_DISPLAY_LIMIT = 100;
    private boolean showingAll = false;
    private Parcelable pendingRecyclerState = null;
    private static final String DATABASE_URL =
            "https://lokasighthama-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final String[] CAMERA_CAPTURE_GROUPS = {"mobile", "depan", "belakang"};

    private interface CameraSnapshotsCallback {
        void onData(List<DataSnapshot> snapshots);
        void onCancelled(DatabaseError error);
    }

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
        btnLoadAllCameraHistory = findViewById(R.id.btnLoadAllCameraHistory);

        btnBackHistory.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        layoutManager = new GridLayoutManager(this, 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter != null && adapter.isFooterPosition(position) ? 2 : 1;
            }
        });
        recyclerView.setLayoutManager(layoutManager);

        adapter = new CameraAdapter(allImages);
        adapter.setOnLoadAllClickListener(() -> {
            pendingRecyclerState = layoutManager == null
                    ? null
                    : layoutManager.onSaveInstanceState();
            showingAll = true;
            adapter.setLoadAllLoading(true);
            adapter.notifyItemChanged(adapter.getItemCount() - 1);
            loadImagesFromFirebase();
        });
        recyclerView.setAdapter(adapter);

        cameraCapturesRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("camera_captures");

        cameraSummaryRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("summary")
                .child("camera");

        if (btnLoadAllCameraHistory != null) {
            btnLoadAllCameraHistory.setVisibility(View.GONE);
        }

        setInitialSummary();

        loadCountsFromSummary();
        loadImagesFromFirebase();
    }

    private void setInitialSummary() {
        tvTotalHistory.setText("0");
        tvTodayHistory.setText("0");
    }

    private void loadCountsFromSummary() {
        cameraSummaryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int totalCount = getSummaryTotalCount(snapshot);
                int todayCount = getSummaryTodayCount(snapshot);

                tvTotalHistory.setText(String.valueOf(totalCount));
                tvTodayHistory.setText(String.valueOf(todayCount));

                Log.d("FIREBASE_HISTORY_COUNT", "Total summary: " + totalCount);
                Log.d("FIREBASE_HISTORY_COUNT", "Today summary: " + todayCount);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(
                        CameraHistoryActivity.this,
                        "Gagal mengambil summary kamera: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private List<String> getRequestedCameraGroups() {
        List<String> groups = new ArrayList<>();

        if (sourceFilter.equals("mobile")) {
            groups.add("mobile");
            return groups;
        }

        if (sourceFilter.equals("cctv")) {
            groups.add("depan");
            groups.add("belakang");
            return groups;
        }

        if (!labelFilter.trim().isEmpty()) {
            String labelLower = labelFilter.toLowerCase(Locale.US);

            if (labelLower.contains("mobile") || labelLower.contains("hp")) {
                groups.add("mobile");
                return groups;
            }

            if (labelLower.contains("belakang") || labelLower.equals("b")) {
                groups.add("belakang");
                return groups;
            }

            if (labelLower.contains("depan") || labelLower.equals("a")) {
                groups.add("depan");
                return groups;
            }
        }

        Collections.addAll(groups, CAMERA_CAPTURE_GROUPS);
        return groups;
    }

    private void loadCameraSnapshotsByGroup(CameraSnapshotsCallback callback) {
        List<String> groups = getRequestedCameraGroups();
        List<DataSnapshot> snapshots = new ArrayList<>();
        int[] pending = {groups.size()};
        boolean[] cancelled = {false};

        for (String group : groups) {
            Query query = cameraCapturesRef
                    .child(group)
                    .orderByChild("time_info/uploaded_at");

            if (!showingAll) {
                query = query.limitToLast(INITIAL_DISPLAY_LIMIT);
            }

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot data : snapshot.getChildren()) {
                        snapshots.add(data);
                    }

                    pending[0]--;
                    if (pending[0] == 0 && !cancelled[0]) {
                        callback.onData(snapshots);
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    if (!cancelled[0]) {
                        cancelled[0] = true;
                        callback.onCancelled(error);
                    }
                }
            });
        }
    }

    private void loadImagesFromFirebase() {
        loadCameraSnapshotsByGroup(new CameraSnapshotsCallback() {
            @Override
            public void onData(List<DataSnapshot> snapshots) {
                allImages.clear();

                List<CameraImage> filteredImages = new ArrayList<>();

                Collections.sort(snapshots, (left, right) -> Long.compare(
                        getCameraUploadedAt(right),
                        getCameraUploadedAt(left)
                ));

                for (DataSnapshot data : snapshots) {
                    String filename = getCameraFilename(data);
                    String imageUrl = getCameraImageUrl(data);
                    String label = getCameraLabel(data);
                    String source = getCameraSource(data);
                    String time = getCameraDisplayTime(data);

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (isGenericCameraFilename(filename)) {
                        filename = getCameraFilenameFromImageUrl(imageUrl);
                    }

                    if (isGenericCameraFilename(filename)) {
                        filename = data.getKey();
                    }

                    if (isGenericCameraFilename(filename)) {
                        filename = "Tidak diketahui";
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

                    if (!matchesLabelFilter(label, filename)) {
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

                int limit = showingAll
                        ? filteredImages.size()
                        : Math.min(filteredImages.size(), INITIAL_DISPLAY_LIMIT);

                for (int i = 0; i < limit; i++) {
                    allImages.add(filteredImages.get(i));
                }

                adapter.setShowLoadAllFooter(!showingAll && allImages.size() >= INITIAL_DISPLAY_LIMIT);
                adapter.setLoadAllLoading(false);

                adapter.notifyDataSetChanged();

                if (pendingRecyclerState != null && layoutManager != null) {
                    final Parcelable state = pendingRecyclerState;
                    pendingRecyclerState = null;
                    recyclerView.post(() -> layoutManager.onRestoreInstanceState(state));
                }

                Log.d("FIREBASE_HISTORY", "Data sesuai filter: " + filteredImages.size());
                Log.d("FIREBASE_HISTORY", "Data yang ditampilkan: " + allImages.size());
                Log.d("FIREBASE_HISTORY", "Source filter: " + sourceFilter);
                Log.d("FIREBASE_HISTORY", "Label filter: " + labelFilter);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if (showingAll && pendingRecyclerState != null) {
                    showingAll = false;
                    pendingRecyclerState = null;
                    adapter.setLoadAllLoading(false);
                    adapter.setShowLoadAllFooter(allImages.size() >= INITIAL_DISPLAY_LIMIT);
                    adapter.notifyDataSetChanged();
                }

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

    private boolean matchesLabelFilter(String label, String filename) {
        if (labelFilter.trim().isEmpty()) {
            return true;
        }

        String filterLower = labelFilter.toLowerCase(Locale.US);
        String labelLower = label == null ? "" : label.toLowerCase(Locale.US);
        String filenameLower = filename == null ? "" : filename.toLowerCase(Locale.US);

        if (labelLower.equals(filterLower)) {
            return true;
        }

        if (filterLower.equals("a") || filterLower.contains("depan")) {
            return labelLower.contains("depan") || filenameLower.contains("depan");
        }

        if (filterLower.equals("b") || filterLower.contains("belakang")) {
            return labelLower.contains("belakang") || filenameLower.contains("belakang");
        }

        if (filterLower.equals("hp") || filterLower.contains("mobile")) {
            return isMobileSource(label, "", filename);
        }

        return false;
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

    private String getFirstSafeString(DataSnapshot data, String... paths) {
        for (String path : paths) {
            String value = getSafeString(data.child(path));

            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private String getCameraImageUrl(DataSnapshot data) {
        return getFirstSafeString(
                data,
                "image_info/image_url",
                "image/image_url",
                "image",
                "image_url",
                "url",
                "download_url",
                "firebase_url"
        );
    }

    private String getCameraFilename(DataSnapshot data) {
        String filename = getFirstSafeString(
                data,
                "filename",
                "file_name",
                "image_info/filename",
                "image_info/file_name",
                "image/filename",
                "image/file_name"
        );

        if (filename != null && !filename.trim().isEmpty()) {
            return filename;
        }

        String key = data.getKey();

        if (key != null && !key.trim().isEmpty()) {
            filename = getFirstSafeString(
                    data,
                    key + "/filename",
                    key + "/file_name",
                    key + "/image_info/filename",
                    key + "/image_info/file_name",
                    key + "/image/filename",
                    key + "/image/file_name"
            );

            if (filename != null && !filename.trim().isEmpty()) {
                return filename;
            }
        }

        String id = getFirstSafeString(data, "id", "capture_id", "key");

        if (id != null && !id.trim().isEmpty()) {
            filename = getFirstSafeString(
                    data,
                    id + "/filename",
                    id + "/file_name",
                    id + "/image_info/filename",
                    id + "/image_info/file_name",
                    id + "/image/filename",
                    id + "/image/file_name"
            );

            if (filename != null && !filename.trim().isEmpty()) {
                return filename;
            }
        }

        filename = getCameraFilenameFromImageUrl(getCameraImageUrl(data));

        if (filename != null && !filename.trim().isEmpty()) {
            return filename;
        }

        if (key != null && !key.trim().isEmpty()) {
            return key;
        }

        return "";
    }

    private boolean isGenericCameraFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return true;
        }

        return filename.trim().equalsIgnoreCase("Foto Kamera");
    }

    private String getCameraFilenameFromImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return "";
        }

        String cleanUrl = imageUrl;
        int queryIndex = cleanUrl.indexOf('?');

        if (queryIndex >= 0) {
            cleanUrl = cleanUrl.substring(0, queryIndex);
        }

        cleanUrl = Uri.decode(cleanUrl);

        int slashIndex = cleanUrl.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < cleanUrl.length() - 1) {
            cleanUrl = cleanUrl.substring(slashIndex + 1);
        }

        if (cleanUrl.trim().isEmpty()) {
            return "";
        }

        return cleanUrl;
    }

    private String getCameraLabel(DataSnapshot data) {
        return getFirstSafeString(
                data,
                "source_info/label",
                "source_info/source",
                "id/point_id",
                "id/capture_id",
                "firebase_key",
                "label",
                "source",
                "point",
                "titik"
        );
    }

    private String getCameraSource(DataSnapshot data) {
        return getFirstSafeString(data, "source_info/source", "source");
    }

    private String getCameraDisplayTime(DataSnapshot data) {
        return getFirstSafeString(
                data,
                "time_info/display",
                "time_info/uploaded_at_iso",
                "time_info/uploaded_at",
                "time/created_at_iso",
                "time/timestamp_iso",
                "time/created_at",
                "time/timestamp",
                "time",
                "timestamp",
                "uploaded_at_iso",
                "uploaded_at"
        );
    }

    private long getCameraUploadedAt(DataSnapshot data) {
        String value = getFirstSafeString(
                data,
                "time_info/uploaded_at",
                "uploaded_at",
                "time/uploaded_at"
        );

        try {
            long parsedValue = Long.parseLong(value);

            if (parsedValue > 0 && parsedValue < 100000000000L) {
                return parsedValue * 1000;
            }

            return parsedValue;
        } catch (Exception ignored) {
            String isoValue = getFirstSafeString(
                    data,
                    "time_info/uploaded_at_iso",
                    "time/created_at_iso",
                    "time/timestamp_iso",
                    "uploaded_at_iso"
            );

            return parseCameraIsoTime(isoValue);
        }
    }

    private long parseCameraIsoTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }

        try {
            return java.time.OffsetDateTime
                    .parse(value)
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int getIntValue(DataSnapshot snapshot) {
        Integer intValue = snapshot.getValue(Integer.class);

        if (intValue != null) {
            return intValue;
        }

        Long longValue = snapshot.getValue(Long.class);

        if (longValue != null) {
            return longValue.intValue();
        }

        String text = getSafeString(snapshot);

        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getSummaryTotalCount(DataSnapshot snapshot) {
        if (sourceFilter.equals("mobile")) {
            return getIntValue(snapshot.child("mobile_count"));
        }

        if (sourceFilter.equals("cctv")) {
            return getIntValue(snapshot.child("cctv_count"));
        }

        return getIntValue(snapshot.child("total_count"));
    }

    private int getSummaryTodayCount(DataSnapshot snapshot) {
        String summaryDate = getSafeString(snapshot.child("today_date"));
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        if (!summaryDate.trim().isEmpty() && !summaryDate.equals(todayDate)) {
            return 0;
        }

        if (sourceFilter.equals("mobile") && snapshot.child("mobile_today").exists()) {
            return getIntValue(snapshot.child("mobile_today"));
        }

        if (sourceFilter.equals("cctv") && snapshot.child("cctv_today").exists()) {
            return getIntValue(snapshot.child("cctv_today"));
        }

        if (sourceFilter.equals("mobile") && snapshot.child("today_mobile_count").exists()) {
            return getIntValue(snapshot.child("today_mobile_count"));
        }

        if (sourceFilter.equals("cctv") && snapshot.child("today_cctv_count").exists()) {
            return getIntValue(snapshot.child("today_cctv_count"));
        }

        return getIntValue(snapshot.child("today_count"));
    }
}
