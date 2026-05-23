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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvTemperature, tvHumidity;
    private TextView tvLastUpdate;
    private TextView tvTemperatureStatus, tvHumidityStatus;
    private LinearLayout cardTemperatureSensor, cardHumiditySensor;

    private TextView tvSeeAll;
    private TextView tvSeeAllDetection;
    private TextView tvCarouselLabel;

    private FrameLayout btnNotification;
    private TextView tvNotificationBadge;

    private ImageButton btnOpenCamera;
    private TextView btnCaptureNow;
    private TextView btnScanPest;

    private LineChart temperatureGraph, humidityGraph;
    private LinearLayout alertContainer;
    private LinearLayout handledAlertContainer;

    private ViewPager2 viewPagerLatestImages;
    private LatestImageAdapter latestImageAdapter;
    private FrameLayout btnLogout;

    private final List<String> latestImageUrls = new ArrayList<>();
    private final List<String> latestImageLabels = new ArrayList<>();
    private final List<AlertPanel> alertList = new ArrayList<>();

    private DatabaseReference sensorLatestRef;
    private DatabaseReference sensorHistoryRef;
    private DatabaseReference cameraCapturesRef;
    private DatabaseReference diseaseResultRef;
    private DatabaseReference pestResultRef;

    private ImageView imgPlantLeft, imgPlantRight, imgPlantHp;
    private TextView tvPlantLeftTime, tvPlantRightTime, tvPlantHpTime;
    private TextView tvPlantLeftStatus, tvPlantRightStatus, tvPlantHpStatus;
    private TextView tvPlantLeftLabel, tvPlantRightLabel, tvPlantHpLabel;
    private LinearLayout cardPlantLeft, cardPlantRight, cardPlantHp;

    private LinearLayout cardSourceCctv, cardSourceMobile;
    private TextView tvCctvImageCount, tvMobileImageCount;

    private TextView tvTotalDetectedCount;
    private TextView tvPestDetectedCount;
    private TextView tvDiseaseDetectedCount;
    private TextView tvUnhandledHandlingCount;
    private TextView tvHandledHandlingCount;


    private ScrollView dashboardScrollView;
    private View cameraMenuOverlay;
    private LinearLayout floatingCameraMenu;
    private FrameLayout btnFloatCamera;
    private View btnFloatPest, btnFloatDisease;
    private LinearLayout cardTotalDetected;
    private LinearLayout cardPestDetected;
    private LinearLayout cardDiseaseDetected;
    private LinearLayout cardUnhandledHandling;
    private LinearLayout cardHandledHandling;

    private boolean isFloatMenuOpen = false;
    private boolean isFloatingHiddenAtBottom = false;
    private boolean showHandledHistory = false;
    private boolean treatmentDiseaseLoaded = false;
    private boolean treatmentPestLoaded = false;
    private boolean latestDiseaseLoaded = false;
    private boolean latestPestLoaded = false;
    private long latestSensorUpdateMillis = -1L;
    private long latestImageUpdateMillis = -1L;

    private View dotCarousel1, dotCarousel2, dotCarousel3, dotCarousel4;
    private final List<View> carouselDots = new ArrayList<>();
    private final List<DetectionAlertItem> treatmentHistoryItems = new ArrayList<>();
    private final List<DetectionAlertItem> latestDetectionItems = new ArrayList<>();

    private static final String PREF_NOTIF = "notification_pref";
    private static final String PREF_READ_KEYS = "read_notification_keys";
    private static final String DATABASE_URL =
            "https://lokasighthama-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler carouselHandler = new Handler(Looper.getMainLooper());

    private final Runnable lastUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateLastUpdateText();
            mainHandler.postDelayed(this, 1000);
        }
    };

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
        loadCameraSourceCounts();
        loadDetectionSummary();
        mainHandler.post(lastUpdateRunnable);

        mainHandler.postDelayed(() -> {
            loadTemperatureGraph();
            loadHumidityGraph();
        }, 700);

        mainHandler.postDelayed(() -> {
            loadLatestDiseaseDetections();
            loadTreatmentSummary();
        }, 900);
    }

    private void initView() {
        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);

        tvTemperatureStatus = findViewById(R.id.tvTemperatureStatus);
        tvHumidityStatus = findViewById(R.id.tvHumidityStatus);
        cardTemperatureSensor = findViewById(R.id.cardTemperatureSensor);
        cardHumiditySensor = findViewById(R.id.cardHumiditySensor);

        tvSeeAll = findViewById(R.id.tvSeeAll);
        tvSeeAllDetection = findViewById(R.id.tvSeeAllDetection);
        tvCarouselLabel = findViewById(R.id.tvCarouselLabel);

        btnNotification = findViewById(R.id.btnNotification);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        btnLogout = findViewById(R.id.btnLogout);

        btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnCaptureNow = findViewById(R.id.btnCaptureNow);
        btnScanPest = findViewById(R.id.btnScanPest);

        temperatureGraph = findViewById(R.id.temperatureGraph);
        humidityGraph = findViewById(R.id.humidityGraph);

        alertContainer = findViewById(R.id.alertContainer);
        handledAlertContainer = findViewById(R.id.handledAlertContainer);

        tvTotalDetectedCount = findViewById(R.id.tvTotalDetectedCount);
        tvPestDetectedCount = findViewById(R.id.tvPestDetectedCount);
        tvDiseaseDetectedCount = findViewById(R.id.tvDiseaseDetectedCount);
        tvUnhandledHandlingCount = findViewById(R.id.tvUnhandledHandlingCount);
        tvHandledHandlingCount = findViewById(R.id.tvHandledHandlingCount);

        cardTotalDetected = findViewById(R.id.cardTotalDetected);
        cardPestDetected = findViewById(R.id.cardPestDetected);
        cardDiseaseDetected = findViewById(R.id.cardDiseaseDetected);
        cardUnhandledHandling = findViewById(R.id.cardUnhandledHandling);
        cardHandledHandling = findViewById(R.id.cardHandledHandling);

        cardSourceCctv = findViewById(R.id.cardSourceCctv);
        cardSourceMobile = findViewById(R.id.cardSourceMobile);

        tvCctvImageCount = findViewById(R.id.tvCctvImageCount);
        tvMobileImageCount = findViewById(R.id.tvMobileImageCount);

        dotCarousel1 = findViewById(R.id.dotCarousel1);
        dotCarousel2 = findViewById(R.id.dotCarousel2);
        dotCarousel3 = findViewById(R.id.dotCarousel3);
        dotCarousel4 = findViewById(R.id.dotCarousel4);

        carouselDots.clear();
        carouselDots.add(dotCarousel1);
        carouselDots.add(dotCarousel2);
        carouselDots.add(dotCarousel3);
        carouselDots.add(dotCarousel4);

        dashboardScrollView = findViewById(R.id.dashboardScrollView);
        cameraMenuOverlay = findViewById(R.id.cameraMenuOverlay);
        floatingCameraMenu = findViewById(R.id.floatingCameraMenu);
        btnFloatCamera = findViewById(R.id.btnFloatCamera);
        btnFloatPest = findViewById(R.id.btnFloatPest);
        btnFloatDisease = findViewById(R.id.btnFloatDisease);

        if (cameraMenuOverlay != null) {
            cameraMenuOverlay.setVisibility(View.GONE);
        }

        if (btnFloatPest != null) {
            btnFloatPest.setVisibility(View.GONE);
        }

        if (btnFloatDisease != null) {
            btnFloatDisease.setVisibility(View.GONE);
        }

        viewPagerLatestImages = findViewById(R.id.viewPagerLatestImages);
        viewPagerLatestImages.setOverScrollMode(View.OVER_SCROLL_NEVER);

        latestImageAdapter = new LatestImageAdapter(latestImageUrls);
        viewPagerLatestImages.setAdapter(latestImageAdapter);

        viewPagerLatestImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                if (position >= 0 && position < latestImageLabels.size()) {
                    tvCarouselLabel.setText(latestImageLabels.get(position));
                }

                updateCarouselDots(position);
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
        sensorLatestRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("sensor")
                .child("dht22")
                .child("latest");

        sensorHistoryRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("sensor")
                .child("dht22")
                .child("history");

        cameraCapturesRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("camera_captures");

        diseaseResultRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("inference_result")
                .child("disease");

        pestResultRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("inference_result")
                .child("pest");
    }

    private void setupButton() {
        btnOpenCamera.setOnClickListener(v -> openCameraWithMode("disease"));

        btnCaptureNow.setOnClickListener(v -> openCameraWithMode("disease"));

        if (btnScanPest != null) {
            btnScanPest.setOnClickListener(v -> openCameraWithMode("pest"));
        }

        tvSeeAll.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraHistoryActivity.class);
            startActivity(intent);
        });

        tvSeeAllDetection.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, DetectionHistoryActivity.class);
            startActivity(intent);
        });

        cardSourceCctv.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraHistoryActivity.class);
            intent.putExtra("source_filter", "cctv");
            startActivity(intent);
        });

        cardSourceMobile.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, CameraHistoryActivity.class);
            intent.putExtra("source_filter", "mobile");
            startActivity(intent);
        });

        btnNotification.setOnClickListener(v -> showNotificationDialog());

        if (cardTemperatureSensor != null) {
            cardTemperatureSensor.setOnClickListener(v -> openSensorDetail("temperature"));
        }

        if (cardHumiditySensor != null) {
            cardHumiditySensor.setOnClickListener(v -> openSensorDetail("humidity"));
        }

        cardPlantLeft.setOnClickListener(v -> openHistoryByLabel("A"));
        cardPlantRight.setOnClickListener(v -> openHistoryByLabel("B"));
        cardPlantHp.setOnClickListener(v -> openHistoryByLabel("HP"));

        if (btnFloatCamera != null) {
            btnFloatCamera.setOnClickListener(v -> toggleFloatingCameraMenu());
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showLogoutDialog());
        }

        if (cameraMenuOverlay != null) {
            cameraMenuOverlay.setOnClickListener(v -> closeFloatingCameraMenu());
        }

        if (btnFloatPest != null) {
            btnFloatPest.setOnClickListener(v -> {
                openCameraWithMode("pest");
                closeFloatingCameraMenu();
            });
        }

        if (btnFloatDisease != null) {
            btnFloatDisease.setOnClickListener(v -> {
                openCameraWithMode("disease");
                closeFloatingCameraMenu();
            });
        }

        if (cardTotalDetected != null) {
            cardTotalDetected.setOnClickListener(v -> openDetectionHistoryWithFilter("all"));
        }

        if (cardPestDetected != null) {
            cardPestDetected.setOnClickListener(v -> openDetectionHistoryWithFilter("pest"));
        }

        if (cardDiseaseDetected != null) {
            cardDiseaseDetected.setOnClickListener(v -> openDetectionHistoryWithFilter("disease"));
        }

        if (cardUnhandledHandling != null) {
            cardUnhandledHandling.setOnClickListener(v -> openTreatmentHistory("unhandled"));
        }

        if (cardHandledHandling != null) {
            cardHandledHandling.setOnClickListener(v -> openTreatmentHistory("handled"));
        }

        setupFloatingButtonScrollBehavior();
    }

    private void toggleFloatingCameraMenu() {
        if (isFloatMenuOpen) {
            closeFloatingCameraMenu();
        } else {
            openFloatingCameraMenu();
        }
    }

    private void openFloatingCameraMenu() {
        if (isFloatingHiddenAtBottom) {
            return;
        }

        isFloatMenuOpen = true;

        if (cameraMenuOverlay != null) {
            cameraMenuOverlay.setVisibility(View.VISIBLE);
            cameraMenuOverlay.setAlpha(0f);
            cameraMenuOverlay.animate()
                    .alpha(1f)
                    .setDuration(180)
                    .start();
        }

        if (btnFloatDisease != null) {
            btnFloatDisease.setVisibility(View.VISIBLE);
            btnFloatDisease.setAlpha(0f);
            btnFloatDisease.setTranslationY(40f);
            btnFloatDisease.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start();
        }

        if (btnFloatPest != null) {
            btnFloatPest.setVisibility(View.VISIBLE);
            btnFloatPest.setAlpha(0f);
            btnFloatPest.setTranslationY(40f);
            btnFloatPest.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .start();
        }

        if (btnFloatCamera != null) {
            btnFloatCamera.animate()
                    .rotation(45f)
                    .setDuration(180)
                    .start();
        }
    }

    private void closeFloatingCameraMenu() {
        isFloatMenuOpen = false;

        if (btnFloatPest != null) {
            btnFloatPest.animate()
                    .alpha(0f)
                    .translationY(40f)
                    .setDuration(140)
                    .withEndAction(() -> btnFloatPest.setVisibility(View.GONE))
                    .start();
        }

        if (btnFloatDisease != null) {
            btnFloatDisease.animate()
                    .alpha(0f)
                    .translationY(40f)
                    .setDuration(140)
                    .withEndAction(() -> btnFloatDisease.setVisibility(View.GONE))
                    .start();
        }

        if (cameraMenuOverlay != null) {
            cameraMenuOverlay.animate()
                    .alpha(0f)
                    .setDuration(140)
                    .withEndAction(() -> cameraMenuOverlay.setVisibility(View.GONE))
                    .start();
        }

        if (btnFloatCamera != null) {
            btnFloatCamera.animate()
                    .rotation(0f)
                    .setDuration(180)
                    .start();
        }
    }

    private void showLogoutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_logout);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView btnCancelLogout = dialog.findViewById(R.id.btnCancelLogout);
        TextView btnConfirmLogout = dialog.findViewById(R.id.btnConfirmLogout);

        btnCancelLogout.setOnClickListener(v -> dialog.dismiss());

        btnConfirmLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            dialog.dismiss();
        });

        dialog.show();

        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void openDetectionHistoryWithFilter(String modeFilter) {
        Intent intent = new Intent(DashboardActivity.this, DetectionHistoryActivity.class);
        intent.putExtra("mode_filter", modeFilter);
        startActivity(intent);
    }

    private void openSensorDetail(String sensorType) {
        Intent intent = new Intent(DashboardActivity.this, SensorDetailActivity.class);
        intent.putExtra("sensor_type", sensorType);
        startActivity(intent);
    }

    private void openTreatmentHistory(String handlingFilter) {
        Intent intent = new Intent(DashboardActivity.this, TreatmentHistoryActivity.class);
        intent.putExtra("handling_filter", handlingFilter);
        startActivity(intent);
    }

    private void openCameraWithMode(String mode) {
        Intent intent = new Intent(DashboardActivity.this, CameraCaptureActivity.class);
        intent.putExtra("scan_mode", mode);
        startActivity(intent);
    }

    private void setupFloatingButtonScrollBehavior() {
        if (dashboardScrollView == null || floatingCameraMenu == null) {
            return;
        }

        dashboardScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            View child = dashboardScrollView.getChildAt(0);

            if (child == null) {
                return;
            }

            int scrollY = dashboardScrollView.getScrollY();
            int scrollViewHeight = dashboardScrollView.getHeight();
            int contentHeight = child.getHeight();

            boolean isAtBottom = scrollY + scrollViewHeight >= contentHeight - dpToPx(80);

            if (isAtBottom) {
                isFloatingHiddenAtBottom = true;

                if (isFloatMenuOpen) {
                    closeFloatingCameraMenu();
                }

                if (floatingCameraMenu.getVisibility() == View.VISIBLE) {
                    floatingCameraMenu.animate()
                            .alpha(0f)
                            .translationY(dpToPx(100))
                            .setDuration(180)
                            .withEndAction(() -> floatingCameraMenu.setVisibility(View.GONE))
                            .start();
                }

            } else {
                isFloatingHiddenAtBottom = false;

                if (floatingCameraMenu.getVisibility() != View.VISIBLE) {
                    floatingCameraMenu.setVisibility(View.VISIBLE);
                    floatingCameraMenu.setAlpha(0f);
                    floatingCameraMenu.setTranslationY(dpToPx(100));
                }

                floatingCameraMenu.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .start();
            }
        });
    }

    private void updateCarouselDots(int activePosition) {
        for (int i = 0; i < carouselDots.size(); i++) {
            View dot = carouselDots.get(i);

            if (dot == null) {
                continue;
            }

            if (i < latestImageUrls.size()) {
                dot.setVisibility(View.VISIBLE);

                if (i == activePosition) {
                    dot.setBackgroundResource(R.drawable.bg_dot_indicator_active);
                } else {
                    dot.setBackgroundResource(R.drawable.bg_dot_indicator_inactive);
                }
            } else {
                dot.setVisibility(View.GONE);
            }
        }
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

        diseaseResultRef.orderByChild("time/timestamp")
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
            String status = getSafeString(data.child("status"));

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

            String className = getDetectionClassNameForCount(data, "disease");
            String source = getDetectionSource(data);
            String timestamp = getDetectionTimestamp(data);
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

        String sensorTimestamp = getSafeString(sensorSnapshot.child("time"));

        if (sensorTimestamp == null || sensorTimestamp.trim().isEmpty()) {
            sensorTimestamp = "-";
        }

        if (suhu != null) {
            if (suhu < 25 || suhu > 28) {
                String title = suhu < 25 ? "Peringatan Suhu Rendah" : "Peringatan Suhu Tinggi";

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
                String title = humidity < 65 ? "Peringatan Kelembaban Rendah" : "Peringatan Kelembaban Tinggi";

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
                .limitToLast(100);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<LatestPhoto> allPhotos = new ArrayList<>();
                long latestUploadedAt = -1L;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String imageUrl = getSafeString(data.child("image"));
                    String label = getSafeString(data.child("label"));
                    String filename = getSafeString(data.child("filename"));
                    String time = getSafeString(data.child("time"));
                    long uploadedAt = getMillisFromSnapshot(data, "uploaded_at", "timestamp", "time");

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    if (uploadedAt > latestUploadedAt) {
                        latestUploadedAt = uploadedAt;
                    }

                    if (label == null || label.trim().isEmpty()) {
                        label = "Foto Kamera";
                    }

                    if (filename == null || filename.trim().isEmpty()) {
                        filename = label;
                    }

                    if (time == null || time.trim().isEmpty()) {
                        time = "-";
                    }

                    allPhotos.add(new LatestPhoto(
                            imageUrl,
                            label,
                            filename,
                            time
                    ));
                }

                Collections.reverse(allPhotos);
                latestImageUpdateMillis = latestUploadedAt;
                updateLastUpdateText();

                List<LatestPhoto> selectedPhotos = new ArrayList<>();

                int mobileCount = 0;
                boolean hasFront = false;
                boolean hasBack = false;

                for (LatestPhoto photo : allPhotos) {
                    String labelLower = photo.label == null
                            ? ""
                            : photo.label.toLowerCase(Locale.US);

                    String filenameLower = photo.filename == null
                            ? ""
                            : photo.filename.toLowerCase(Locale.US);

                    if (isMobilePhoto(labelLower, filenameLower)) {
                        if (mobileCount < 2) {
                            selectedPhotos.add(photo);
                            mobileCount++;
                        }
                    } else if (isFrontPhoto(labelLower, filenameLower)) {
                        if (!hasFront) {
                            selectedPhotos.add(photo);
                            hasFront = true;
                        }
                    } else if (isBackPhoto(labelLower, filenameLower)) {
                        if (!hasBack) {
                            selectedPhotos.add(photo);
                            hasBack = true;
                        }
                    }

                    if (mobileCount >= 2 && hasFront && hasBack) {
                        break;
                    }
                }

                for (LatestPhoto photo : allPhotos) {
                    if (selectedPhotos.size() >= 4) {
                        break;
                    }

                    boolean alreadyAdded = false;

                    for (LatestPhoto selected : selectedPhotos) {
                        if (selected.imageUrl.equals(photo.imageUrl)) {
                            alreadyAdded = true;
                            break;
                        }
                    }

                    if (!alreadyAdded) {
                        selectedPhotos.add(photo);
                    }
                }

                latestImageUrls.clear();
                latestImageLabels.clear();

                for (LatestPhoto photo : selectedPhotos) {
                    latestImageUrls.add(photo.imageUrl);
                    latestImageLabels.add(formatCarouselLabel(photo));
                }

                latestImageAdapter.notifyDataSetChanged();

                if (!latestImageLabels.isEmpty()) {
                    tvCarouselLabel.setText(latestImageLabels.get(0));
                    viewPagerLatestImages.setCurrentItem(0, false);
                    updateCarouselDots(0);
                } else {
                    tvCarouselLabel.setText("Belum Ada Foto");
                    updateCarouselDots(0);
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

    private boolean isMobilePhoto(String label, String filename) {
        if (label == null) label = "";
        if (filename == null) filename = "";

        return label.contains("mobile")
                || label.contains("hp")
                || filename.contains("mobile")
                || filename.contains("hp");
    }

    private boolean isMobileCameraSource(String label, String source, String filename) {
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

    private boolean isFrontPhoto(String label, String filename) {
        if (label == null) label = "";
        if (filename == null) filename = "";

        return label.contains("depan")
                || label.contains("front")
                || label.equals("a")
                || label.contains("titik a")
                || filename.contains("depan")
                || filename.contains("_a")
                || filename.contains("titik_a");
    }

    private boolean isBackPhoto(String label, String filename) {
        if (label == null) label = "";
        if (filename == null) filename = "";

        return label.contains("belakang")
                || label.contains("back")
                || label.equals("b")
                || label.contains("titik b")
                || filename.contains("belakang")
                || filename.contains("_b")
                || filename.contains("titik_b");
    }

    private String formatCarouselLabel(LatestPhoto photo) {
        String label = photo.label == null ? "" : photo.label;
        String filename = photo.filename == null ? "" : photo.filename.replace("_", " ");

        String labelLower = label.toLowerCase(Locale.US);
        String filenameLower = photo.filename == null ? "" : photo.filename.toLowerCase(Locale.US);

        if (isMobilePhoto(labelLower, filenameLower)) {
            return "MOBILE - " + filename;
        }

        if (isFrontPhoto(labelLower, filenameLower)) {
            return "CCTV - DEPAN - " + filename;
        }

        if (isBackPhoto(labelLower, filenameLower)) {
            return "CCTV - BELAKANG - " + filename;
        }

        return label + " - " + filename;
    }

    private void startAutoCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable);

        if (latestImageUrls.size() > 1) {
            carouselHandler.postDelayed(carouselRunnable, 3000);
        }
    }

    private void loadPlantPointCards() {
        Query query = cameraCapturesRef
                .orderByChild("uploaded_at")
                .limitToLast(30);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                LatestPhoto leftPhoto = null;
                LatestPhoto rightPhoto = null;
                LatestPhoto hpPhoto = null;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String imageUrl = getSafeString(data.child("image"));
                    String label = getSafeString(data.child("label"));
                    String time = getSafeString(data.child("time"));

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
                    } else if (label.equalsIgnoreCase("HP") || label.equalsIgnoreCase("mobile")) {
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

    private void loadCameraSourceCounts() {
        cameraCapturesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int cctvCount = 0;
                int mobileCount = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String label = getSafeString(data.child("label"));
                    String source = getSafeString(data.child("source"));
                    String filename = getSafeString(data.child("filename"));
                    String imageUrl = getSafeString(data.child("image"));

                    if (imageUrl == null || imageUrl.trim().isEmpty()) {
                        continue;
                    }

                    boolean isMobile = isMobileCameraSource(label, source, filename);

                    if (isMobile) {
                        mobileCount++;
                    } else {
                        cctvCount++;
                    }
                }

                tvCctvImageCount.setText(cctvCount + " gambar");
                tvMobileImageCount.setText(mobileCount + " gambar");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvCctvImageCount.setText("Gagal memuat");
                tvMobileImageCount.setText("Gagal memuat");
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
                .thumbnail(0.25f)
                .override(300, 220)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .placeholder(R.drawable.plant)
                .error(R.drawable.plant)
                .into(imageView);

        timeView.setText("Update: " + photo.time);

        if (photo.label.equalsIgnoreCase("HP") || photo.label.equalsIgnoreCase("mobile")) {
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
        latestDetectionItems.clear();
        latestDiseaseLoaded = false;
        latestPestLoaded = false;

        loadLatestDetectionsByMode(diseaseResultRef, "disease");
        loadLatestDetectionsByMode(pestResultRef, "pest");
    }

    private void loadLatestDetectionsByMode(DatabaseReference reference, String mode) {
        Query query = reference
                .orderByChild("time/timestamp")
                .limitToLast(100);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                removeLatestDetectionItemsByMode(mode);

                for (DataSnapshot data : snapshot.getChildren()) {
                    DetectionAlertItem item = createLatestDetectionItem(data, mode);

                    if (item != null) {
                        latestDetectionItems.add(item);
                    }
                }

                setLatestModeLoaded(mode);
                renderLatestDetections();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLatestModeLoaded(mode);
                renderLatestDetections();
            }
        });
    }

    private DetectionAlertItem createLatestDetectionItem(DataSnapshot data, String defaultMode) {
        String detectionKey = data.getKey();

        if (detectionKey == null || detectionKey.trim().isEmpty()
                || detectionKey.equalsIgnoreCase("latest")) {
            return null;
        }

        if (isDetectionHandled(data)) {
            return null;
        }

        String className = getDetectionClassNameForCount(data, defaultMode);
        String imageUrl = getDetectionImageUrl(data);
        String mode = getSafeString(data.child("mode"));
        String recommendation = getSafeString(data.child("recommendation"));
        String source = getDetectionSource(data);
        String timestamp = getDetectionTimestamp(data);
        Double confidenceValue = getDetectionConfidenceForMode(data, defaultMode);

        if (className == null || className.trim().isEmpty()
                || className.equalsIgnoreCase("healthy")
                || className.equalsIgnoreCase("unknown")) {
            return null;
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

        if (recommendation == null || recommendation.trim().isEmpty()) {
            recommendation = getSafeString(data.child("recommendation_detail").child("summary"));
        }

        if (recommendation == null || recommendation.trim().isEmpty()) {
            recommendation = "Belum ada rekomendasi penanganan.";
        }

        double confidencePercent = 0;

        if (confidenceValue != null) {
            confidencePercent = confidenceValue * 100;
        }

        String description =
                "Mode: " + mode +
                        "\nSource: " + source +
                        "\nConfidence: " + String.format(Locale.US, "%.2f", confidencePercent) + "%";

        AlertPanel alert = new AlertPanel(
                source,
                formatClassName(className),
                imageUrl,
                timestamp,
                description,
                recommendation
        );

        return new DetectionAlertItem(
                detectionKey,
                alert,
                defaultMode,
                false,
                timestamp,
                getMillisFromValue(timestamp)
        );
    }

    private void renderLatestDetections() {
        if (!latestDiseaseLoaded || !latestPestLoaded || alertContainer == null) {
            return;
        }

        alertContainer.removeAllViews();
        alertList.clear();

        latestDetectionItems.sort((left, right) -> {
            int millisCompare = Long.compare(right.timestampMillis, left.timestampMillis);

            if (millisCompare != 0) {
                return millisCompare;
            }

            String leftTime = left.timestamp == null ? "" : left.timestamp;
            String rightTime = right.timestamp == null ? "" : right.timestamp;
            return rightTime.compareTo(leftTime);
        });

        int shownCount = 0;

        for (DetectionAlertItem item : latestDetectionItems) {
            if (shownCount >= 5) {
                break;
            }

            alertList.add(item.alert);
            addAlertPanel(item.alert, item.detectionKey, item.mode);
            shownCount++;
        }

        if (shownCount == 0) {
            AlertPanel emptyAlert = new AlertPanel(
                    "Sistem",
                    "Tidak Ada Deteksi Aktif",
                    "placeholder",
                    "-",
                    "Semua deteksi sudah ditangani.",
                    "Tidak ada alert baru yang perlu ditampilkan."
            );

            addAlertPanel(emptyAlert, null, "disease");
        }
    }

    private void removeLatestDetectionItemsByMode(String mode) {
        for (int i = latestDetectionItems.size() - 1; i >= 0; i--) {
            DetectionAlertItem item = latestDetectionItems.get(i);

            if (item.mode != null && item.mode.equalsIgnoreCase(mode)) {
                latestDetectionItems.remove(i);
            }
        }
    }

    private void setLatestModeLoaded(String mode) {
        if (mode != null && mode.equalsIgnoreCase("pest")) {
            latestPestLoaded = true;
        } else {
            latestDiseaseLoaded = true;
        }
    }

    private void loadLatestHandledDetections() {
        Query query = diseaseResultRef
                .orderByChild("time/timestamp")
                .limitToLast(100);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                handledAlertContainer.removeAllViews();

                List<DetectionAlertItem> handledItems = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    String detectionKey = data.getKey();

                    Boolean handled = data.child("handled").getValue(Boolean.class);
                    String status = getSafeString(data.child("status"));

                    boolean isHandled = false;

                    if (handled != null && handled) {
                        isHandled = true;
                    }

                    if (status != null && status.equalsIgnoreCase("handled")) {
                        isHandled = true;
                    }

                    if (!isHandled) {
                        continue;
                    }

                    String className = getDetectionClassNameForCount(data, "disease");
                    String imageUrl = getDetectionImageUrl(data);
                    String mode = getSafeString(data.child("mode"));
                    String recommendation = getSafeString(data.child("recommendation"));
                    String source = getDetectionSource(data);
                    String timestamp = getDetectionTimestamp(data);
                    String handledAt = getDetectionHandledAt(data);

                    Double confidenceValue = data.child("confidence").getValue(Double.class);

                    if (recommendation == null || recommendation.trim().isEmpty()) {
                        recommendation = getSafeString(
                                data.child("recommendation_detail").child("summary")
                        );
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

                    if (handledAt == null || handledAt.trim().isEmpty()) {
                        handledAt = "-";
                    }

                    if (recommendation == null || recommendation.trim().isEmpty()) {
                        recommendation = "Deteksi sudah ditandai selesai.";
                    }

                    double confidencePercent = 0;

                    if (confidenceValue != null) {
                        confidencePercent = confidenceValue * 100;
                    }

                    String displayName = formatClassName(className);

                    String description =
                            "Status: Selesai Ditangani" +
                                    "\nMode: " + mode +
                                    "\nSource: " + source +
                                    "\nConfidence: " + String.format(Locale.US, "%.2f", confidencePercent) + "%" +
                                    "\nDitangani: " + handledAt;

                    AlertPanel alert = new AlertPanel(
                            source,
                            displayName,
                            imageUrl,
                            timestamp,
                            description,
                            recommendation
                    );

                    handledItems.add(new DetectionAlertItem(detectionKey, alert));
                }

                Collections.reverse(handledItems);

                int shownCount = 0;

                for (DetectionAlertItem item : handledItems) {
                    if (shownCount >= 5) {
                        break;
                    }

                    addHandledAlertPanel(item.alert);
                    shownCount++;
                }

                if (shownCount == 0) {
                    addEmptyHandledCard();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                handledAlertContainer.removeAllViews();

                AlertPanel errorAlert = new AlertPanel(
                        "Firebase",
                        "Gagal Memuat Data",
                        "placeholder",
                        "-",
                        "Data selesai ditangani gagal diambil dari Firebase.",
                        error.getMessage()
                );

                addHandledAlertPanel(errorAlert);
            }
        });
    }

    private void addHandledAlertPanel(AlertPanel alert) {
        AlertView view = new AlertView(DashboardActivity.this);

        view.setData(
                alert.imageUrl,
                alert.date,
                alert.diseaseName,
                alert.description,
                alert.solution
        );

        view.setSolveButtonVisible(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(0, 0, dpToPx(12), 0);

        view.setLayoutParams(params);
        handledAlertContainer.addView(view);
    }

    private void addEmptyHandledCard() {
        AlertPanel emptyAlert = new AlertPanel(
                "Sistem",
                "Belum Ada Deteksi Selesai",
                "placeholder",
                "-",
                "Belum ada deteksi yang ditandai selesai.",
                "Tekan tombol Tandai Selesai pada deteksi aktif untuk memindahkannya ke bagian ini."
        );

        addHandledAlertPanel(emptyAlert);
    }

    private void loadTreatmentSummary() {
        diseaseResultRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                pestResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot pestSnapshot) {
                        updateTreatmentSummaryCounts(diseaseSnapshot, pestSnapshot);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        updateTreatmentSummaryCounts(diseaseSnapshot, null);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvUnhandledHandlingCount.setText("0");
                tvHandledHandlingCount.setText("0");
            }
        });

        pestResultRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot pestSnapshot) {
                diseaseResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                        updateTreatmentSummaryCounts(diseaseSnapshot, pestSnapshot);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        updateTreatmentSummaryCounts(null, pestSnapshot);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvUnhandledHandlingCount.setText("0");
                tvHandledHandlingCount.setText("0");
            }
        });
    }

    private void updateTreatmentSummaryCounts(
            DataSnapshot diseaseSnapshot,
            DataSnapshot pestSnapshot
    ) {
        int unhandledCount = 0;
        int handledCount = 0;

        if (diseaseSnapshot != null) {
            int[] diseaseCounts = countTreatmentStatus(diseaseSnapshot, "disease");
            unhandledCount += diseaseCounts[0];
            handledCount += diseaseCounts[1];
        }

        if (pestSnapshot != null) {
            int[] pestCounts = countTreatmentStatus(pestSnapshot, "pest");
            unhandledCount += pestCounts[0];
            handledCount += pestCounts[1];
        }

        tvUnhandledHandlingCount.setText(String.valueOf(unhandledCount));
        tvHandledHandlingCount.setText(String.valueOf(handledCount));
    }

    private int[] countTreatmentStatus(DataSnapshot snapshot, String mode) {
        int unhandledCount = 0;
        int handledCount = 0;

        for (DataSnapshot data : snapshot.getChildren()) {
            String key = data.getKey();

            if (key != null && key.equalsIgnoreCase("latest")) {
                continue;
            }

            String className = getDetectionClassNameForCount(data, mode);
            String imageUrl = getDetectionImageUrl(data);

            if (className == null || className.trim().isEmpty()
                    || className.equalsIgnoreCase("healthy")
                    || className.equalsIgnoreCase("unknown")) {
                continue;
            }

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                continue;
            }

            boolean handled = isDetectionHandled(data);

            if (handled) {
                handledCount++;
            } else {
                unhandledCount++;
            }
        }

        return new int[]{unhandledCount, handledCount};
    }

    private void loadTreatmentHistory() {
        treatmentHistoryItems.clear();
        treatmentDiseaseLoaded = false;
        treatmentPestLoaded = false;

        loadTreatmentHistoryByMode(diseaseResultRef, "disease");
        loadTreatmentHistoryByMode(pestResultRef, "pest");
    }

    private void loadTreatmentHistoryByMode(DatabaseReference reference, String mode) {
        Query query = reference
                .orderByChild("time/timestamp")
                .limitToLast(100);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                removeTreatmentItemsByMode(mode);

                for (DataSnapshot data : snapshot.getChildren()) {
                    DetectionAlertItem item = createTreatmentHistoryItem(data, mode);

                    if (item != null) {
                        treatmentHistoryItems.add(item);
                    }
                }

                setTreatmentModeLoaded(mode);
                renderTreatmentHistory();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setTreatmentModeLoaded(mode);
                renderTreatmentHistory();
            }
        });
    }

    private DetectionAlertItem createTreatmentHistoryItem(DataSnapshot data, String defaultMode) {
        String detectionKey = data.getKey();

        if (detectionKey == null || detectionKey.trim().isEmpty()
                || detectionKey.equalsIgnoreCase("latest")) {
            return null;
        }

        String className = getDetectionClassNameForCount(data, defaultMode);

        if (className == null || className.trim().isEmpty()
                || className.equalsIgnoreCase("healthy")
                || className.equalsIgnoreCase("unknown")) {
            return null;
        }

        boolean handled = isDetectionHandled(data);

        String imageUrl = getDetectionImageUrl(data);
        String mode = getSafeString(data.child("mode"));
        String recommendation = getSafeString(data.child("recommendation"));
        String source = getDetectionSource(data);
        String timestamp = getDetectionTimestamp(data);
        String handledAt = getDetectionHandledAt(data);
        Double confidenceValue = getDetectionConfidenceForMode(data, defaultMode);

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

        if (recommendation == null || recommendation.trim().isEmpty()) {
            recommendation = getSafeString(data.child("recommendation_detail").child("summary"));
        }

        if (recommendation == null || recommendation.trim().isEmpty()) {
            recommendation = handled
                    ? "Deteksi sudah ditandai selesai."
                    : "Belum ada rekomendasi penanganan.";
        }

        double confidencePercent = 0;

        if (confidenceValue != null) {
            confidencePercent = confidenceValue * 100;
        }

        String description = "Status: " + (handled ? "Selesai Ditangani" : "Belum Ditangani") +
                "\nMode: " + mode +
                "\nSource: " + source +
                "\nConfidence: " + String.format(Locale.US, "%.2f", confidencePercent);

        if (handled && handledAt != null && !handledAt.trim().isEmpty()) {
            description += "\nDitangani: " + handledAt;
        }

        AlertPanel alert = new AlertPanel(
                source,
                formatClassName(className),
                imageUrl,
                timestamp,
                description,
                recommendation
        );

        return new DetectionAlertItem(
                detectionKey,
                alert,
                defaultMode,
                handled,
                timestamp,
                getMillisFromValue(timestamp)
        );
    }

    private void renderTreatmentHistory() {
        if (!treatmentDiseaseLoaded || !treatmentPestLoaded || handledAlertContainer == null) {
            return;
        }

        handledAlertContainer.removeAllViews();

        int unhandledCount = 0;
        int handledCount = 0;
        List<DetectionAlertItem> visibleItems = new ArrayList<>();

        for (DetectionAlertItem item : treatmentHistoryItems) {
            if (item.handled) {
                handledCount++;
            } else {
                unhandledCount++;
            }

            if (item.handled == showHandledHistory) {
                visibleItems.add(item);
            }
        }

        tvUnhandledHandlingCount.setText(String.valueOf(unhandledCount));
        tvHandledHandlingCount.setText(String.valueOf(handledCount));

        visibleItems.sort((left, right) -> {
            String leftTime = left.timestamp == null ? "" : left.timestamp;
            String rightTime = right.timestamp == null ? "" : right.timestamp;
            return rightTime.compareTo(leftTime);
        });

        int shownCount = 0;

        for (DetectionAlertItem item : visibleItems) {
            if (shownCount >= 5) {
                break;
            }

            if (showHandledHistory) {
                addTreatmentAlertPanel(item, false);
            } else {
                addTreatmentAlertPanel(item, true);
            }

            shownCount++;
        }

        if (shownCount == 0) {
            addEmptyTreatmentCard();
        }
    }

    private void addTreatmentAlertPanel(DetectionAlertItem item, boolean showSolveButton) {
        AlertView view = new AlertView(DashboardActivity.this);

        view.setData(
                item.alert.imageUrl,
                item.alert.date,
                item.alert.diseaseName,
                item.alert.description,
                item.alert.solution
        );

        view.setHandledStatus(item.handled);
        view.setSolveButtonVisible(showSolveButton);

        if (showSolveButton) {
            view.setOnSolveClick(() -> markDetectionAsHandled(item.detectionKey, item.mode));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(0, 0, dpToPx(12), 0);

        view.setLayoutParams(params);
        handledAlertContainer.addView(view);
    }

    private void addEmptyTreatmentCard() {
        String title = showHandledHistory
                ? "Belum Ada Deteksi Selesai"
                : "Tidak Ada Deteksi Aktif";
        String description = showHandledHistory
                ? "Belum ada deteksi yang ditandai selesai."
                : "Semua deteksi sudah ditangani.";
        String solution = showHandledHistory
                ? "Tekan kartu Belum Ditangani lalu Tandai Selesai untuk memindahkan data ke sini."
                : "Tidak ada alert baru yang perlu ditampilkan.";

        AlertPanel emptyAlert = new AlertPanel(
                "Sistem",
                title,
                "placeholder",
                "-",
                description,
                solution
        );

        AlertView view = new AlertView(DashboardActivity.this);

        view.setData(
                emptyAlert.imageUrl,
                emptyAlert.date,
                emptyAlert.diseaseName,
                emptyAlert.description,
                emptyAlert.solution
        );

        view.setSolveButtonVisible(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(280),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        params.setMargins(0, 0, dpToPx(12), 0);
        view.setLayoutParams(params);
        handledAlertContainer.addView(view);
    }

    private void removeTreatmentItemsByMode(String mode) {
        for (int i = treatmentHistoryItems.size() - 1; i >= 0; i--) {
            DetectionAlertItem item = treatmentHistoryItems.get(i);

            if (item.mode != null && item.mode.equalsIgnoreCase(mode)) {
                treatmentHistoryItems.remove(i);
            }
        }
    }

    private void setTreatmentModeLoaded(String mode) {
        if (mode != null && mode.equalsIgnoreCase("pest")) {
            treatmentPestLoaded = true;
        } else {
            treatmentDiseaseLoaded = true;
        }
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

            String[] keys = {
                    "summary",
                    "class_name",
                    "predicted_class",
                    "detected_class",
                    "label",
                    "prediction",
                    "value",
                    "annotated_image_url",
                    "url",
                    "image_url",
                    "input_image_url",
                    "image",
                    "path",
                    "public_url",
                    "source",
                    "type",
                    "name",
                    "status",
                    "sync_status",
                    "timestamp_iso",
                    "created_at_iso",
                    "timestamp",
                    "created_at",
                    "time"
            };

            for (String key : keys) {
                Object mappedValue = map.get(key);

                if (mappedValue != null) {
                    return String.valueOf(mappedValue);
                }
            }

            return "";
        }

        return String.valueOf(value);
    }

    private String getDetectionSource(DataSnapshot data) {
        String source = getSafeString(data.child("source"));

        if (source == null || source.trim().isEmpty()) {
            source = getSafeString(data.child("source_info"));
        }

        return source;
    }

    private String getDetectionTimestamp(DataSnapshot data) {
        String timestamp = getSafeString(data.child("timestamp"));

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("timestamp_iso"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("timestamp"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("created_at_iso"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("time").child("created_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("created_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("uploaded_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getSafeString(data.child("captured_at"));
        }

        if (timestamp == null || timestamp.trim().isEmpty()) {
            timestamp = getTimestampFromImageFields(data);
        }

        return timestamp;
    }

    private String getTimestampFromImageFields(DataSnapshot data) {
        String[] imageValues = {
                getSafeString(data.child("input_image_url")),
                getSafeString(data.child("image").child("input_image_url")),
                getSafeString(data.child("image").child("image_url")),
                getSafeString(data.child("image_url")),
                getSafeString(data.child("image"))
        };

        for (String imageValue : imageValues) {
            String timestamp = extractCompactTimestamp(imageValue);

            if (timestamp != null && !timestamp.trim().isEmpty()) {
                return timestamp;
            }
        }

        return "";
    }

    private String extractCompactTimestamp(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        Matcher matcher = Pattern.compile("(\\d{8}_\\d{6})").matcher(value);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    private String getDetectionHandledAt(DataSnapshot data) {
        String handledAt = getSafeString(data.child("status").child("handled_at"));

        if (handledAt == null || handledAt.trim().isEmpty()) {
            handledAt = getSafeString(data.child("handled_at"));
        }

        return handledAt;
    }

    private boolean isDetectionHandled(DataSnapshot data) {
        Boolean handledValue = data.child("handled").getValue(Boolean.class);

        if (handledValue != null && handledValue) {
            return true;
        }

        Boolean nestedHandledValue = data.child("status").child("handled").getValue(Boolean.class);

        if (nestedHandledValue != null && nestedHandledValue) {
            return true;
        }

        String status = getSafeString(data.child("status"));

        return status != null && status.equalsIgnoreCase("handled");
    }

    private void updateLastUpdateText() {
        if (tvLastUpdate == null) {
            return;
        }

        long latestUpdateMillis = Math.max(latestSensorUpdateMillis, latestImageUpdateMillis);

        if (latestUpdateMillis <= 0) {
            tvLastUpdate.setText("Terakhir diupdate -");
            return;
        }

        tvLastUpdate.setText("Terakhir diupdate " + formatRelativeTime(latestUpdateMillis));
    }

    private String formatRelativeTime(long timestampMillis) {
        long diffMillis = System.currentTimeMillis() - timestampMillis;

        if (diffMillis < 0) {
            diffMillis = 0;
        }

        long seconds = diffMillis / 1000;

        if (seconds < 5) {
            return "baru saja";
        }

        if (seconds < 60) {
            return seconds + " detik yang lalu";
        }

        long minutes = seconds / 60;

        if (minutes < 60) {
            return minutes + " menit yang lalu";
        }

        long hours = minutes / 60;

        if (hours < 24) {
            return hours + " jam yang lalu";
        }

        long days = hours / 24;

        if (days < 7) {
            return days + " hari yang lalu";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", new Locale("id", "ID"));
        return sdf.format(new Date(timestampMillis));
    }

    private long getMillisFromSnapshot(DataSnapshot data, String... keys) {
        for (String key : keys) {
            long millis = getMillisFromValue(data.child(key).getValue());

            if (millis > 0) {
                return millis;
            }
        }

        return -1L;
    }

    private long getMillisFromValue(Object value) {
        if (value == null) {
            return -1L;
        }

        if (value instanceof Number) {
            long rawValue = ((Number) value).longValue();

            if (rawValue <= 0) {
                return -1L;
            }

            if (rawValue < 100000000000L) {
                return rawValue * 1000L;
            }

            return rawValue;
        }

        String text = String.valueOf(value).trim();

        if (text.isEmpty() || text.equals("-")) {
            return -1L;
        }

        try {
            long rawValue = Long.parseLong(text);

            if (rawValue < 100000000000L) {
                return rawValue * 1000L;
            }

            return rawValue;
        } catch (Exception ignored) {
        }

        String[] utcPatterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyyMMdd_HHmmss"
        };

        if (text.matches(".*\\.\\d{4,}([+-]\\d{2}:\\d{2}|Z)$")) {
            text = text.replaceFirst("\\.(\\d{3})\\d+([+-]\\d{2}:\\d{2}|Z)$", ".$1$2");
        }

        for (String pattern : utcPatterns) {
            long millis = parseDateMillis(text, pattern, TimeZone.getTimeZone("UTC"));

            if (millis > 0) {
                return millis;
            }
        }

        String[] localPatterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm"
        };

        for (String pattern : localPatterns) {
            long millis = parseDateMillis(text, pattern, TimeZone.getDefault());

            if (millis > 0) {
                return millis;
            }
        }

        return -1L;
    }

    private long parseDateMillis(String text, String pattern, TimeZone timeZone) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
            sdf.setLenient(false);
            sdf.setTimeZone(timeZone);

            Date date = sdf.parse(text);

            if (date != null) {
                return date.getTime();
            }
        } catch (Exception ignored) {
        }

        return -1L;
    }

    private void loadLatestSensorData() {
        sensorLatestRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double suhu = snapshot.child("temperature").getValue(Double.class);
                Double humidity = snapshot.child("humidity").getValue(Double.class);
                latestSensorUpdateMillis = getMillisFromSnapshot(
                        snapshot,
                        "updated_at",
                        "timestamp",
                        "time",
                        "created_at"
                );
                updateLastUpdateText();

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

    private void loadDetectionSummary() {
        diseaseResultRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                pestResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot pestSnapshot) {
                        int diseaseCount = countActiveDetections(diseaseSnapshot, "disease");
                        int pestCount = countActiveDetections(pestSnapshot, "pest");
                        int totalCount = diseaseCount + pestCount;

                        tvDiseaseDetectedCount.setText(String.valueOf(diseaseCount));
                        tvPestDetectedCount.setText(String.valueOf(pestCount));
                        tvTotalDetectedCount.setText(String.valueOf(totalCount));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        int diseaseCount = countActiveDetections(diseaseSnapshot, "disease");

                        tvDiseaseDetectedCount.setText(String.valueOf(diseaseCount));
                        tvPestDetectedCount.setText("0");
                        tvTotalDetectedCount.setText(String.valueOf(diseaseCount));
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvDiseaseDetectedCount.setText("0");
                tvPestDetectedCount.setText("0");
                tvTotalDetectedCount.setText("0");
            }
        });

        pestResultRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot pestSnapshot) {
                diseaseResultRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot diseaseSnapshot) {
                        int diseaseCount = countActiveDetections(diseaseSnapshot, "disease");
                        int pestCount = countActiveDetections(pestSnapshot, "pest");
                        int totalCount = diseaseCount + pestCount;

                        tvDiseaseDetectedCount.setText(String.valueOf(diseaseCount));
                        tvPestDetectedCount.setText(String.valueOf(pestCount));
                        tvTotalDetectedCount.setText(String.valueOf(totalCount));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        int pestCount = countActiveDetections(pestSnapshot, "pest");

                        tvDiseaseDetectedCount.setText("0");
                        tvPestDetectedCount.setText(String.valueOf(pestCount));
                        tvTotalDetectedCount.setText(String.valueOf(pestCount));
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvPestDetectedCount.setText("0");
            }
        });
    }

    private int countActiveDetections(DataSnapshot snapshot, String mode) {
        int count = 0;

        for (DataSnapshot data : snapshot.getChildren()) {
            String key = data.getKey();

            if (key != null && key.equalsIgnoreCase("latest")) {
                continue;
            }

            String className = getDetectionClassNameForCount(data, mode);
            String imageUrl = getDetectionImageUrl(data);

            if (className == null || className.trim().isEmpty()) {
                continue;
            }

            if (className.equalsIgnoreCase("healthy")) {
                continue;
            }

            if (className.equalsIgnoreCase("unknown")) {
                continue;
            }

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                continue;
            }

            count++;
        }

        return count;
    }

    private void updateDetectionSummaryOnlyDisease(DataSnapshot diseaseSnapshot) {
        int diseaseCount = countActiveDetections(diseaseSnapshot, "disease");

        tvDiseaseDetectedCount.setText(String.valueOf(diseaseCount));
        tvPestDetectedCount.setText("0");
        tvTotalDetectedCount.setText(String.valueOf(diseaseCount));
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
        addAlertPanel(alert, detectionKey, "disease");
    }

    private void addAlertPanel(AlertPanel alert, String detectionKey, String mode) {
        AlertView view = new AlertView(DashboardActivity.this);

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

            markDetectionAsHandled(detectionKey, mode);
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
        markDetectionAsHandled(detectionKey, "disease");
    }

    private void markDetectionAsHandled(String detectionKey, String mode) {
        Map<String, Object> updates = new HashMap<>();

        updates.put("handled", true);
        updates.put("status", "handled");
        updates.put("handled_at", getCurrentIsoTime());

        DatabaseReference targetRef = mode != null && mode.equalsIgnoreCase("pest")
                ? pestResultRef
                : diseaseResultRef;

        targetRef.child(detectionKey)
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

    private String getDetectionClassNameForCount(DataSnapshot data, String mode) {
        if (mode != null && mode.equalsIgnoreCase("pest")) {
            DataSnapshot bestPest = getBestPestSnapshot(data);

            if (bestPest != null) {
                String pestClassName = getSafeString(bestPest.child("class_name"));

                if (pestClassName == null || pestClassName.trim().isEmpty()) {
                    pestClassName = getSafeString(bestPest.child("detected_class"));
                }

                if (pestClassName == null || pestClassName.trim().isEmpty()) {
                    pestClassName = getSafeString(bestPest.child("label"));
                }

                if (pestClassName != null && !pestClassName.trim().isEmpty()) {
                    return pestClassName;
                }
            }
        }

        String className = getSafeString(data.child("class_name"));

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("detected_class"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("prediction"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("label"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("disease_name"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("result").child("class_name"));
        }

        if (className == null || className.trim().isEmpty()) {
            className = getSafeString(data.child("result").child("detected_class"));
        }

        if (className == null || className.trim().isEmpty()) {
            if (mode != null && mode.equalsIgnoreCase("pest")) {
                className = "Hama Terdeteksi";
            } else if (mode != null && mode.equalsIgnoreCase("disease")) {
                className = "Penyakit Terdeteksi";
            } else {
                className = "Deteksi";
            }
        }

        return className;
    }

    private Double getDetectionConfidenceForMode(DataSnapshot data, String mode) {
        if (mode != null && mode.equalsIgnoreCase("pest")) {
            DataSnapshot bestPest = getBestPestSnapshot(data);

            if (bestPest != null) {
                Double pestConfidence = bestPest.child("confidence").getValue(Double.class);

                if (pestConfidence != null) {
                    return pestConfidence;
                }
            }
        }

        Double confidence = data.child("confidence").getValue(Double.class);

        if (confidence != null) {
            return confidence;
        }

        confidence = data.child("prediction").child("confidence").getValue(Double.class);

        if (confidence != null) {
            return confidence;
        }

        confidence = data.child("prediction").child("score").getValue(Double.class);

        if (confidence != null) {
            return confidence;
        }

        return data.child("result").child("confidence").getValue(Double.class);
    }

    private String getDetectionImageUrl(DataSnapshot data) {
        String imageUrl = getSafeString(data.child("image_url"));

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = getSafeString(data.child("image"));
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = getSafeString(data.child("imageUrl"));
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = getSafeString(data.child("url"));
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = getSafeString(data.child("result").child("image_url"));
        }

        return imageUrl;
    }

    private DataSnapshot getBestPestSnapshot(DataSnapshot data) {
        DataSnapshot bestDetectionSnapshot = data.child("prediction").child("best_detection");

        if (bestDetectionSnapshot.exists()) {
            return bestDetectionSnapshot;
        }

        DataSnapshot pestSnapshot = data.child("pest");

        if (!pestSnapshot.exists()) {
            return null;
        }

        DataSnapshot bestSnapshot = null;
        double bestConfidence = -1;

        for (DataSnapshot pestItem : pestSnapshot.getChildren()) {
            Double confidence = pestItem.child("confidence").getValue(Double.class);

            if (confidence == null) {
                continue;
            }

            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestSnapshot = pestItem;
            }
        }

        return bestSnapshot;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        carouselHandler.removeCallbacks(carouselRunnable);
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static class LatestPhoto {
        String imageUrl;
        String label;
        String filename;
        String time;

        LatestPhoto(String imageUrl, String label) {
            this.imageUrl = imageUrl;
            this.label = label;
            this.filename = label;
            this.time = "-";
        }

        LatestPhoto(String imageUrl, String label, String time) {
            this.imageUrl = imageUrl;
            this.label = label;
            this.filename = label;
            this.time = time;
        }

        LatestPhoto(String imageUrl, String label, String filename, String time) {
            this.imageUrl = imageUrl;
            this.label = label;
            this.filename = filename;
            this.time = time;
        }
    }

    private static class DetectionAlertItem {
        String detectionKey;
        AlertPanel alert;
        String mode;
        boolean handled;
        String timestamp;
        long timestampMillis;

        DetectionAlertItem(String detectionKey, AlertPanel alert) {
            this.detectionKey = detectionKey;
            this.alert = alert;
            this.mode = "disease";
            this.handled = false;
            this.timestamp = alert == null ? "" : alert.date;
            this.timestampMillis = 0L;
        }

        DetectionAlertItem(
                String detectionKey,
                AlertPanel alert,
                String mode,
                boolean handled,
                String timestamp,
                long timestampMillis
        ) {
            this.detectionKey = detectionKey;
            this.alert = alert;
            this.mode = mode;
            this.handled = handled;
            this.timestamp = timestamp;
            this.timestampMillis = timestampMillis;
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
