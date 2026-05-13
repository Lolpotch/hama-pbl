package com.example.penyakitan;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SplashActivity extends AppCompatActivity {

    private ImageView imgSplashBackground, imgSplashLogo;
    private TextView tvSplashTitle, tvSplashSubtitle;
    private View viewLoadingLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        imgSplashBackground = findViewById(R.id.imgSplashBackground);
        imgSplashLogo = findViewById(R.id.imgSplashLogo);
        tvSplashTitle = findViewById(R.id.tvSplashTitle);
        tvSplashSubtitle = findViewById(R.id.tvSplashSubtitle);
        viewLoadingLine = findViewById(R.id.viewLoadingLine);

        startSplashAnimation();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }, 2800);
    }

    private void startSplashAnimation() {
        // Background zoom pelan
        ObjectAnimator bgScaleX = ObjectAnimator.ofFloat(imgSplashBackground, "scaleX", 1f, 1.12f);
        ObjectAnimator bgScaleY = ObjectAnimator.ofFloat(imgSplashBackground, "scaleY", 1f, 1.12f);

        bgScaleX.setDuration(2800);
        bgScaleY.setDuration(2800);
        bgScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        bgScaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        // Logo bounce + fade
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(imgSplashLogo, "alpha", 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(imgSplashLogo, "scaleX", 0.75f, 1.08f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(imgSplashLogo, "scaleY", 0.75f, 1.08f, 1f);

        logoAlpha.setDuration(850);
        logoScaleX.setDuration(900);
        logoScaleY.setDuration(900);
        logoScaleX.setInterpolator(new OvershootInterpolator());
        logoScaleY.setInterpolator(new OvershootInterpolator());

        // Judul muncul dari bawah
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(tvSplashTitle, "alpha", 0f, 1f);
        ObjectAnimator titleMove = ObjectAnimator.ofFloat(tvSplashTitle, "translationY", 35f, 0f);

        titleAlpha.setStartDelay(600);
        titleMove.setStartDelay(600);
        titleAlpha.setDuration(700);
        titleMove.setDuration(700);

        // Subtitle muncul
        ObjectAnimator subtitleAlpha = ObjectAnimator.ofFloat(tvSplashSubtitle, "alpha", 0f, 1f);
        ObjectAnimator subtitleMove = ObjectAnimator.ofFloat(tvSplashSubtitle, "translationY", 25f, 0f);

        subtitleAlpha.setStartDelay(900);
        subtitleMove.setStartDelay(900);
        subtitleAlpha.setDuration(650);
        subtitleMove.setDuration(650);

        // Loading line melebar
        viewLoadingLine.post(() -> {
            ObjectAnimator loadingWidth = ObjectAnimator.ofInt(
                    new WidthWrapper(viewLoadingLine),
                    "width",
                    0,
                    dpToPx(170)
            );

            loadingWidth.setDuration(2200);
            loadingWidth.setStartDelay(450);
            loadingWidth.setInterpolator(new AccelerateDecelerateInterpolator());
            loadingWidth.start();
        });

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                bgScaleX,
                bgScaleY,
                logoAlpha,
                logoScaleX,
                logoScaleY,
                titleAlpha,
                titleMove,
                subtitleAlpha,
                subtitleMove
        );

        animatorSet.start();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    public static class WidthWrapper {
        private final View view;

        public WidthWrapper(View view) {
            this.view = view;
        }

        public int getWidth() {
            return view.getLayoutParams().width;
        }

        public void setWidth(int width) {
            view.getLayoutParams().width = width;
            view.requestLayout();
        }
    }
}