package com.example.penyakitan;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvTemperature, tvHumidity;
    private TextView tvTemperatureStatus, tvHumidityStatus;

    private TextView tvSeeAll;
    private TextView tvSeeAllDetection;
    private TextView tvCarouselLabel;

    private FrameLayout btnNotification;
    private TextView tvNotificationBadge;

    private ImageButton btnOpenCamera;
    private TextView btnCaptureNow;

    private LineChart temperatureGraph, humidityGraph;
    private LinearLayout alertContainer;

    private ViewPager2 viewPagerLatestImages;
    private LatestImageAdapter latestImageAdapter;

    private final List<String> latestImageUrls = new ArrayList<>();
    private final List<String> latestImageLabels = new ArrayList<>();
    private final List<AlertPanel> alertList = new ArrayList<>();

    private DatabaseReference sensorLatestRef;
    private DatabaseReference sensorHistoryRef;
    private DatabaseReference cameraCapturesRef;
    private DatabaseReference diseaseResultRef;

    private ImageView imgPlantLeft, imgPlantRight, imgPlantHp;
    private TextView tvPlantLeftTime, tvPlantRightTime, tvPlantHpTime;
    private TextView tvPlantLeftStatus, tvPlantRightStatus, tvPlantHpStatus;
    private TextView tvPlantLeftLabel, tvPlantRightLabel, tvPlantHpLabel;
    private LinearLayout cardPlantLeft, cardPlantRight, cardPlantHp;

    private static final String PREF_NOTIF = "notification_pref";
    private static final String PREF_READ_KEYS = "read_notification_keys";

    private final Handler carouselHandler = new Handler(Looper.getMainLooper());

    private final Runnable carouselRunnable = new Runnable() {
        @Override
        public void run() {
            if (latestImageUrls.size() > 1 && viewPagerLatestImages != null) {
                int currentItem = viewPagerLatestImages.getCurrentItem();
                int nextItem = currentItem + 1;

                if (nextItem >= latestImageUrls.size()) {
                    nextItem = 0;
                }

                viewPagerLatestImages.setCurrentItem(nextItem, true);
            }

            carouselHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initView();
        initFirebase();
        setupButton();

        loadNotificationBadge();
        loadLatestCarouselImages();
        loadPlantPointCards();
        loadLatestSensorData();
        loadTemperatureGraph();
        loadHumidityGraph();
        loadLatestDiseaseDetections();
    }

    private void initView() {
        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity = findViewById(R.id.tvHumidity);

        tvTemperatureStatus = findViewById(R.id.tvTemperatureStatus);
        tvHumidityStatus = findViewById(R.id.tvHumidityStatus);

        tvSeeAll = findViewById(R.id.tvSeeAll);
        tvSeeAllDetection = findViewById(R.id.tvSeeAllDetection);
        tvCarouselLabel = findViewById(R.id.tvCarouselLabel);

        btnNotification = findViewById(R.id.btnNotification);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);

        btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnCaptureNow = findViewById(R.id.btnCaptureNow);

        temperatureGraph = findViewById(R.id.temperatureGraph);
        humidityGraph = findViewById(R.id.humidityGraph);

        alertContainer = findViewById(R.id.alertContainer);

        viewPagerLatestImages = findViewById(R.id.viewPagerLatestImages);
        latestImageAdapter = new LatestImageAdapter(latestImageUrls);
        viewPagerLatestImages.setAdapter(latestImageAdapter);

        viewPagerLatestImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                if (position >= 0 && position < latestImageLabels.size()) {
                    tvCarouselLabel.setText(latestImageLabels.get(position));
                }
            }
        });

        imgPlantLeft = findViewById(R.id.imgPlantLeft);
        imgPlantRight = findViewById(R.id.imgPlantRight);
        imgPlantHp = findViewById(R.id.imgPlantHp);

        tvPlantLeftTime = findViewById(R.id.tvPlantLeftTime);
        tvPlantRightTime = findViewById(R.id.tvPlantRightTime);
        tvPlantHpTime = findViewById(R.id.tvPlantHpTime);

        tvPlantLeftStatus = findViewById(R.id.tvPlantLeftStatus);
        tvPlantRightStatus = findViewById(R.id.tvPlantRightStatus);
        tvPlantHpStatus = findViewById(R.id.tvPlantHpStatus);

        tvPlantLeftLabel = findViewById(R.id.tvPlantLeftLabel);
        tvPlantRightLabel = findViewById(R.id.tvPlantRightLabel);
        tvPlantHpLabel = findViewById(R.id.tvPlantHpLabel);

        cardPlantLeft = findViewById(R.id.cardPlantLeft);
        cardPlantRight = findViewById(R.id.cardPlantRight);
        cardPlantHp = findViewById(R.id.cardPlantHp);
    }

    private void initFirebase() {
        sensorLatestRef = FirebaseDatabase.getInstance()
                .getReference("sensor")
                .child("dht22")
                .child("latest");

        sensorHistoryRef = FirebaseDatabase.getInstance()
                .getReference("sensor")
                .child("dht22")
                .child("history");

        cameraCapturesRef = FirebaseDatabase.getInstance()
                .getReference("camera_captures");

        diseaseResultRef = FirebaseDatabase.getInstance()
                .getReference("inference_result")
                .child("disease");
    }

    private void setupButton() {
        btnOpenCamera.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraCaptureActivity.class);
            startActivity(intent);
        });

        btnCaptureNow.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraCaptureActivity.class);
            startActivity(intent);
        });

        tvSeeAll.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraHistoryActivity.class);
            startActivity(intent);
        });

        tvSeeAllDetection.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, DetectionHistoryActivity.class);
            startActivity(intent);
        });

        btnNotification.setOnClickListener(v -> showNotificationDialog());

        cardPlantLeft.setOnClickListener(v -> openHistoryByLabel("A"));
        cardPlantRight.setOnClickListener(v -> openHistoryByLabel("B"));
        cardPlantHp.setOnClickListener(v -> openHistoryByLabel("HP"));
    }

    private void openHistoryByLabel(String label) {
        Intent intent = new Intent(DashboardActivity.this, CameraHistoryActivity.class);
        intent.putExtra("label_filter", label);
        startActivity(intent);
    }

    private void loadNotificationBadge() {
        diseaseResultRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                sensorLatestRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot sensorSnapshot) {
                        List<NotificationItem> items = buildNotificationItems(diseaseSnapshot, sensorSnapshot);

                        int unreadCount = 0;

                        for (NotificationItem item : items) {
                            if (!isNotificationRead(item.notificationKey)) {
                                unreadCount++;
                            }
                        }

                        updateNotificationBadge(unreadCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        updateNotificationBadge(0);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateNotificationBadge(0);
            }
        });

        sensorLatestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot sensorSnapshot) {
                diseaseResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                        List<NotificationItem> items = buildNotificationItems(diseaseSnapshot, sensorSnapshot);

                        int unreadCount = 0;

                        for (NotificationItem item : items) {
                            if (!isNotificationRead(item.notificationKey)) {
                                unreadCount++;
                            }
                        }

                        updateNotificationBadge(unreadCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        updateNotificationBadge(0);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                updateNotificationBadge(0);
            }
        });
    }

    private void updateNotificationBadge(int count) {
        if (tvNotificationBadge == null) return;

        if (count > 0) {
            tvNotificationBadge.setVisibility(View.VISIBLE);

            if (count > 99) {
                tvNotificationBadge.setText("99+");
            } else {
                tvNotificationBadge.setText(String.valueOf(count));
            }
        } else {
            tvNotificationBadge.setVisibility(View.GONE);
        }
    }

    private void showNotificationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_notification);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout notificationListContainer = dialog.findViewById(R.id.notificationListContainer);
        TextView tvDialogNotifCount = dialog.findViewById(R.id.tvDialogNotifCount);
        TextView btnCloseNotification = dialog.findViewById(R.id.btnCloseNotification);
        TextView btnMarkAllRead = dialog.findViewById(R.id.btnMarkAllRead);

        btnCloseNotification.setOnClickListener(v -> dialog.dismiss());

        notificationListContainer.removeAllViews();

        diseaseResultRef.orderByChild("timestamp")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                        sensorLatestRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot sensorSnapshot) {
                                notificationListContainer.removeAllViews();

                                List<NotificationItem> allItems = buildNotificationItems(diseaseSnapshot, sensorSnapshot);
                                List<NotificationItem> unreadItems = new ArrayList<>();

                                for (NotificationItem item : allItems) {
                                    if (!isNotificationRead(item.notificationKey)) {
                                        unreadItems.add(item);
                                    }
                                }

                                tvDialogNotifCount.setText(String.valueOf(unreadItems.size()));

                                if (unreadItems.isEmpty()) {
                                    TextView emptyText = new TextView(DashboardActivity.this);
                                    emptyText.setText("Tidak ada notifikasi baru.");
                                    emptyText.setTextColor(Color.parseColor("#667085"));
                                    emptyText.setTextSize(15);
                                    emptyText.setGravity(android.view.Gravity.CENTER);
                                    emptyText.setPadding(0, dpToPx(50), 0, dpToPx(50));

                                    notificationListContainer.addView(emptyText);
                                } else {
                                    for (NotificationItem item : unreadItems) {
                                        addNotificationItemView(notificationListContainer, item);
                                    }
                                }

                                btnMarkAllRead.setOnClickListener(v -> {
                                    markNotificationsAsRead(unreadItems);
                                    dialog.dismiss();

                                    updateNotificationBadge(0);

                                    Toast.makeText(
                                            DashboardActivity.this,
                                            "Notifikasi ditandai sudah dibaca",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                });
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Toast.makeText(
                                        DashboardActivity.this,
                                        "Gagal memuat sensor: " + error.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(
                                DashboardActivity.this,
                                "Gagal memuat notifikasi: " + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

        dialog.show();

        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private List<NotificationItem> buildNotificationItems(
            DataSnapshot diseaseSnapshot,
            DataSnapshot sensorSnapshot
    ) {
        List<NotificationItem> items = new ArrayList<>();

        for (DataSnapshot data : diseaseSnapshot.getChildren()) {
            String detectionKey = data.getKey();

            Boolean handled = data.child("handled").getValue(Boolean.class);
            String status = data.child("status").getValue(String.class);

            boolean isHandled = false;

            if (handled != null && handled) {
                isHandled = true;
            }

            if (status != null && status.equalsIgnoreCase("handled")) {
                isHandled = true;
            }

            if (isHandled) {
                continue;
            }

            String className = data.child("class_name").getValue(String.class);
            String source = data.child("source").getValue(String.class);
            String timestamp = data.child("timestamp").getValue(String.class);
            Double confidenceValue = data.child("confidence").getValue(Double.class);

            if (detectionKey == null || detectionKey.trim().isEmpty()) {
                detectionKey = "unknown_detection_" + System.currentTimeMillis();
            }

            if (className == null || className.trim().isEmpty()) {
                className = "Deteksi";
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

            items.add(
                    new NotificationItem(
                            "detection_" + detectionKey,
                            "⚠",
                            formatClassName(className) + " Terdeteksi",
                            "Terdeteksi dari " + source +
                                    " dengan confidence " +
                                    String.format(Locale.US, "%.1f", confidencePercent) + "%",
                            formatNotificationTime(timestamp),
                            getPriorityLabel(confidencePercent),
                            confidencePercent
                    )
            );
        }

        Double suhu = sensorSnapshot.child("temperature").getValue(Double.class);
        Double humidity = sensorSnapshot.child("humidity").getValue(Double.class);

        String sensorTimestamp = sensorSnapshot.child("time").getValue(String.class);

        if (sensorTimestamp == null || sensorTimestamp.trim().isEmpty()) {
            sensorTimestamp = "-";
        }

        if (suhu != null) {
            if (suhu < 25 || suhu > 28) {
                String title;

                if (suhu < 25) {
                    title = "Peringatan Suhu Rendah";
                } else {
                    title = "Peringatan Suhu Tinggi";
                }

                items.add(
                        new NotificationItem(
                                "sensor_temperature_" + String.format(Locale.US, "%.1f", suhu),
                                "🌡",
                                title,
                                "Suhu saat ini " + String.format(Locale.US, "%.1f", suhu) +
                                        "°C. Batas normal: 25–28°C.",
                                sensorTimestamp,
                                "High",
                                100
                        )
                );
            }
        }

        if (humidity != null) {
            if (humidity < 65 || humidity > 78) {
                String title;

                if (humidity < 65) {
                    title = "Peringatan Kelembaban Rendah";
                } else {
                    title = "Peringatan Kelembaban Tinggi";
                }

                items.add(
                        new NotificationItem(
                                "sensor_humidity_" + String.format(Locale.US, "%.1f", humidity),
                                "💧",
                                title,
                                "Kelembaban saat ini " + String.format(Locale.US, "%.1f", humidity) +
                                        "% RH. Batas normal: 65–78% RH.",
                                sensorTimestamp,
                                "High",
                                100
                        )
                );
            }
        }

        Collections.reverse(items);

        return items;
    }

    private void addNotificationItemView(
            LinearLayout container,
            NotificationItem item
    ) {
        View view = getLayoutInflater()
                .inflate(R.layout.item_notification, container, false);

        TextView tvNotifIcon = view.findViewById(R.id.tvNotifIcon);
        TextView tvNotifTitle = view.findViewById(R.id.tvNotifTitle);
        TextView tvNotifMessage = view.findViewById(R.id.tvNotifMessage);
        TextView tvNotifTime = view.findViewById(R.id.tvNotifTime);
        TextView tvNotifPriority = view.findViewById(R.id.tvNotifPriority);

        tvNotifIcon.setText(item.icon);
        tvNotifTitle.setText(item.title);
        tvNotifMessage.setText(item.message);
        tvNotifTime.setText(item.time);
        tvNotifPriority.setText(item.priority);

        container.addView(view);
    }

    private void markNotificationsAsRead(List<NotificationItem> notificationItems) {
        if (notificationItems == null || notificationItems.isEmpty()) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NOTIF, MODE_PRIVATE);
        String oldKeys = prefs.getString(PREF_READ_KEYS, "");

        StringBuilder builder = new StringBuilder(oldKeys == null ? "" : oldKeys);

        for (NotificationItem item : notificationItems) {
            if (item.notificationKey == null || item.notificationKey.trim().isEmpty()) {
                continue;
            }

            String wrappedKey = "|" + item.notificationKey + "|";

            if (!builder.toString().contains(wrappedKey)) {
                builder.append(wrappedKey);
            }
        }

        prefs.edit()
                .putString(PREF_READ_KEYS, builder.toString())
                .apply();
    }

    private boolean isNotificationRead(String notificationKey) {
        if (notificationKey == null || notificationKey.trim().isEmpty()) {
            return false;
        }

        SharedPreferences prefs = getSharedPreferences(PREF_NOTIF, MODE_PRIVATE);
        String readKeys = prefs.getString(PREF_READ_KEYS, "");

        if (readKeys == null) {
            readKeys = "";
        }

        return readKeys.contains("|" + notificationKey + "|");
    }

    private String getPriorityLabel(double value) {
        if (value >= 80) {
            return "High";
        } else if (value >= 60) {
            return "Medium";
        } else {
            return "Low";
        }
    }

    private String formatNotificationTime(String timestamp) {
        if (timestamp == null || timestamp.trim().isEmpty() || timestamp.equals("-")) {
            return "-";
        }

        try {
            String[] dateTime = timestamp.split(" ");

            if (dateTime.length < 2) {
                return timestamp;
            }

            String date = dateTime[0];
            String time = dateTime[1];

            String[] dateParts = date.split("-");
            String[] timeParts = time.split(":");

            if (dateParts.length < 3 || timeParts.length < 2) {
                return timestamp;
            }

            int month = Integer.parseInt(dateParts[1]);
            String day = dateParts[2];
            String hour = timeParts[0];
            String minute = timeParts[1];

            String[] months = {
                    "",
                    "Jan",
                    "Feb",
                    "Mar",
                    "Apr",
                    "Mei",
                    "Jun",
                    "Jul",
                    "Agu",
                    "Sep",
                    "Okt",
                    "Nov",
                    "Des"
            };

            String monthName = month >= 1 && month <= 12 ? months[month] : dateParts[1];

            return day + " " + monthName + ", " + hour + "." + minute;

        } catch (Exception e) {
            return timestamp;
        }
    }

    private void loadLatestCarouselImages() {
        Query query = cameraCapturesRef
                .orderByChild("uploaded_at")
                .limitToLast(4);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<LatestPhoto> latestPhotos = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    String imageUrl = data.child("image").getValue(String.class);
                    String label = data.child("label").getValue(String.class);

                    if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                        if (label == null || label.trim().isEmpty()) {
                            label = "Foto Kamera";
                        }

                        latestPhotos.add(new LatestPhoto(imageUrl, label));
                    }
                }

                Collections.reverse(latestPhotos);

                latestImageUrls.clear();
                latestImageLabels.clear();

                for (LatestPhoto photo : latestPhotos) {
                    latestImageUrls.add(photo.imageUrl);
                    latestImageLabels.add(photo.label);
                }

                latestImageAdapter.notifyDataSetChanged();

                if (!latestImageLabels.isEmpty()) {
                    tvCarouselLabel.setText(latestImageLabels.get(0));
                    viewPagerLatestImages.setCurrentItem(0, false);
                } else {
                    tvCarouselLabel.setText("Belum Ada Foto");
                }

                startAutoCarousel();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                latestImageUrls.clear();
                latestImageLabels.clear();
                latestImageAdapter.notifyDataSetChanged();

                tvCarouselLabel.setText("Gagal Memuat Foto");

                carouselHandler.removeCallbacks(carouselRunnable);
            }
        });
    }

    private void startAutoCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable);

        if (latestImageUrls.size() > 1) {
            carouselHandler.postDelayed(carouselRunnable, 3000);
        }
    }

    private void loadPlantPointCards() {
        Query query = cameraCapturesRef.orderByChild("uploaded_at");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                LatestPhoto leftPhoto = null;
                LatestPhoto rightPhoto = null;
                LatestPhoto hpPhoto = null;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String imageUrl = data.child("image").getValue(String.class);
                    String label = data.child("label").getValue(String.class);
                    String time = data.child("time").getValue(String.class);

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (label == null) {
                        label = "";
                    }

                    if (time == null || time.trim().isEmpty()) {
                        time = "-";
                    }

                    LatestPhoto photo = new LatestPhoto(imageUrl, label, time);

                    if (label.equalsIgnoreCase("A")) {
                        leftPhoto = photo;
                    } else if (label.equalsIgnoreCase("B")) {
                        rightPhoto = photo;
                    } else if (label.equalsIgnoreCase("HP")) {
                        hpPhoto = photo;
                    }
                }

                updatePlantCard(leftPhoto, imgPlantLeft, tvPlantLeftTime, tvPlantLeftStatus, tvPlantLeftLabel, "Normal");
                updatePlantCard(rightPhoto, imgPlantRight, tvPlantRightTime, tvPlantRightStatus, tvPlantRightLabel, "Normal");
                updatePlantCard(hpPhoto, imgPlantHp, tvPlantHpTime, tvPlantHpStatus, tvPlantHpLabel, "Mobile");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void updatePlantCard(
            LatestPhoto photo,
            ImageView imageView,
            TextView timeView,
            TextView statusView,
            TextView sourceView,
            String defaultStatus
    ) {
        if (photo == null) {
            timeView.setText("Update: -");
            statusView.setText("Belum Ada");
            statusView.setTextColor(Color.parseColor("#667085"));
            sourceView.setText("Sumber: -");
            return;
        }

        Glide.with(this)
                .load(photo.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.plant)
                .error(R.drawable.plant)
                .into(imageView);

        timeView.setText("Update: " + photo.time);

        if (photo.label.equalsIgnoreCase("HP")) {
            sourceView.setText("Sumber: Mobile");
            statusView.setText("Mobile");
            statusView.setTextColor(Color.parseColor("#2563EB"));
        } else {
            sourceView.setText("Sumber: CCTV");
            statusView.setText(defaultStatus);
            statusView.setTextColor(Color.parseColor("#0B7A2A"));
        }
    }

    private void loadLatestDiseaseDetections() {
        Query query = diseaseResultRef.orderByChild("timestamp");

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                alertContainer.removeAllViews();
                alertList.clear();

                List<DetectionAlertItem> tempAlerts = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    String detectionKey = data.getKey();

                    Boolean handled = data.child("handled").getValue(Boolean.class);
                    String status = data.child("status").getValue(String.class);

                    if (handled != null && handled) {
                        continue;
                    }

                    if (status != null && status.equalsIgnoreCase("handled")) {
                        continue;
                    }

                    String className = data.child("class_name").getValue(String.class);
                    String imageUrl = data.child("image_url").getValue(String.class);
                    String mode = data.child("mode").getValue(String.class);
                    String recommendation = data.child("recommendation").getValue(String.class);
                    String source = data.child("source").getValue(String.class);
                    String timestamp = data.child("timestamp").getValue(String.class);

                    Double confidenceValue = data.child("confidence").getValue(Double.class);

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

                    tempAlerts.add(new DetectionAlertItem(detectionKey, alert));
                }

                Collections.reverse(tempAlerts);

                for (DetectionAlertItem item : tempAlerts) {
                    alertList.add(item.alert);
                    addAlertPanel(item.alert, item.detectionKey);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                alertContainer.removeAllViews();

                AlertPanel errorAlert = new AlertPanel(
                        "Firebase",
                        "Gagal Memuat Deteksi",
                        "placeholder",
                        "-",
                        "Data deteksi gagal diambil dari Firebase.",
                        error.getMessage()
                );

                addAlertPanel(errorAlert, null);
            }
        });
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

    private void loadLatestSensorData() {
        sensorLatestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double suhu = snapshot.child("temperature").getValue(Double.class);
                Double humidity = snapshot.child("humidity").getValue(Double.class);

                if (suhu != null) {
                    tvTemperature.setText(formatNumber(suhu) + "°C");
                    setTemperatureStatus(suhu);
                } else {
                    tvTemperature.setText("--°C");
                    tvTemperatureStatus.setText("Tidak ada data");
                }

                if (humidity != null) {
                    tvHumidity.setText(formatNumber(humidity) + "%");
                    setHumidityStatus(humidity);
                } else {
                    tvHumidity.setText("--%");
                    tvHumidityStatus.setText("Tidak ada data");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvTemperature.setText("--°C");
                tvHumidity.setText("--%");
                tvTemperatureStatus.setText("Error");
                tvHumidityStatus.setText("Error");
            }
        });
    }

    private void setTemperatureStatus(double suhu) {
        if (suhu < 25) {
            tvTemperatureStatus.setText("Rendah");
            tvTemperatureStatus.setTextColor(Color.parseColor("#2563EB"));
        } else if (suhu <= 28) {
            tvTemperatureStatus.setText("Normal");
            tvTemperatureStatus.setTextColor(Color.parseColor("#0B7A2A"));
        } else {
            tvTemperatureStatus.setText("Tinggi");
            tvTemperatureStatus.setTextColor(Color.parseColor("#D92D20"));
        }
    }

    private void setHumidityStatus(double humidity) {
        if (humidity < 65) {
            tvHumidityStatus.setText("Rendah");
            tvHumidityStatus.setTextColor(Color.parseColor("#C4320A"));
        } else if (humidity <= 78) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(Color.parseColor("#0B7A2A"));
        } else {
            tvHumidityStatus.setText("Tinggi");
            tvHumidityStatus.setTextColor(Color.parseColor("#2563EB"));
        }
    }

    private void loadTemperatureGraph() {
        Query query = sensorHistoryRef.limitToLast(10);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Entry> entries = new ArrayList<>();
                int index = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    Double temp = data.child("temperature").getValue(Double.class);

                    if (temp != null) {
                        entries.add(new Entry(index, temp.floatValue()));
                        index++;
                    }
                }

                setupLineChart(
                        temperatureGraph,
                        entries,
                        "Suhu (°C)",
                        Color.parseColor("#FF6B1A")
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadHumidityGraph() {
        Query query = sensorHistoryRef.limitToLast(10);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Entry> entries = new ArrayList<>();
                int index = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    Double humidity = data.child("humidity").getValue(Double.class);

                    if (humidity != null) {
                        entries.add(new Entry(index, humidity.floatValue()));
                        index++;
                    }
                }

                setupLineChart(
                        humidityGraph,
                        entries,
                        "Kelembaban (%)",
                        Color.parseColor("#1E88E5")
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void setupLineChart(LineChart chart, List<Entry> entries, String label, int color) {
        LineDataSet dataSet = new LineDataSet(entries, label);

        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);

        chart.setData(lineData);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);

        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);

        chart.getAxisRight().setEnabled(false);

        chart.getAxisLeft().setTextColor(Color.parseColor("#667085"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#EEEEEE"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#667085"));
        xAxis.setGridColor(Color.parseColor("#EEEEEE"));
        xAxis.setGranularity(1f);

        chart.invalidate();
    }

    private String formatNumber(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format(Locale.US, "%.1f", value);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void addAlertPanel(AlertPanel alert, String detectionKey) {
        AlertView view = new AlertView(this);

        view.setData(
                alert.imageUrl,
                alert.date,
                alert.diseaseName,
                alert.description,
                alert.solution
        );

        view.setSolveButtonVisible(true);

        view.setOnSolveClick(() -> {
            if (detectionKey == null || detectionKey.trim().isEmpty()) {
                Toast.makeText(
                        DashboardActivity.this,
                        "ID deteksi tidak ditemukan",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            markDetectionAsHandled(detectionKey);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(0, 0, dpToPx(12), 0);

        view.setLayoutParams(params);
        alertContainer.addView(view);
    }

    private void markDetectionAsHandled(String detectionKey) {
        Map<String, Object> updates = new HashMap<>();

        updates.put("handled", true);
        updates.put("handled_at", getCurrentIsoTime());
        updates.put("status", "handled");

        diseaseResultRef.child(detectionKey)
                .updateChildren(updates)
                .addOnSuccessListener(unused -> Toast.makeText(
                        DashboardActivity.this,
                        "Deteksi ditandai selesai",
                        Toast.LENGTH_SHORT
                ).show())
                .addOnFailureListener(e -> Toast.makeText(
                        DashboardActivity.this,
                        "Gagal update Firebase: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private String getCurrentIsoTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );

        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        carouselHandler.removeCallbacks(carouselRunnable);
    }

    private static class LatestPhoto {
        String imageUrl;
        String label;
        String time;

        LatestPhoto(String imageUrl, String label) {
            this.imageUrl = imageUrl;
            this.label = label;
            this.time = "-";
        }

        LatestPhoto(String imageUrl, String label, String time) {
            this.imageUrl = imageUrl;
            this.label = label;
            this.time = time;
        }
    }

    private static class DetectionAlertItem {
        String detectionKey;
        AlertPanel alert;

        DetectionAlertItem(String detectionKey, AlertPanel alert) {
            this.detectionKey = detectionKey;
            this.alert = alert;
        }
    }

    private static class NotificationItem {
        String notificationKey;
        String icon;
        String title;
        String message;
        String time;
        String priority;
        double value;

        NotificationItem(
                String notificationKey,
                String icon,
                String title,
                String message,
                String time,
                String priority,
                double value
        ) {
            this.notificationKey = notificationKey;
            this.icon = icon;
            this.title = title;
            this.message = message;
            this.time = time;
            this.priority = priority;
            this.value = value;
        }
    }
}