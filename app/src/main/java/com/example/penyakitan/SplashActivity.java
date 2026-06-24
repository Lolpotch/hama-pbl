package com.example.penyakitan;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SplashActivity extends AppCompatActivity {

    private ImageView imgSplashBackground;
    private ImageView imgSplashLogo;
    private ImageView imgSplashText;
    private View viewLoadingLine;

    private boolean alreadyMoveToLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        imgSplashBackground = findViewById(R.id.imgSplashBackground);
        imgSplashLogo = findViewById(R.id.imgSplashLogo);
        imgSplashText = findViewById(R.id.imgSplashText);
        viewLoadingLine = findViewById(R.id.viewLoadingLine);

        prepareInitialState();
        startSplashAnimation();
    }

    private void prepareInitialState() {
        if (imgSplashBackground != null) {
            imgSplashBackground.setAlpha(0f);
            imgSplashBackground.setScaleX(1f);
            imgSplashBackground.setScaleY(1f);
        }

        if (imgSplashLogo != null) {
            imgSplashLogo.setAlpha(0f);
            imgSplashLogo.setScaleX(0.75f);
            imgSplashLogo.setScaleY(0.75f);
        }

        if (imgSplashText != null) {
            imgSplashText.setAlpha(0f);
            imgSplashText.setTranslationY(dpToPx(25));
        }

        if (viewLoadingLine != null) {
            viewLoadingLine.post(() -> {
                viewLoadingLine.getLayoutParams().width = 0;
                viewLoadingLine.requestLayout();
            });
        }
    }

    private void startSplashAnimation() {
        animateBackground();
        animateLogo();
        animateSplashText();
        animateLoadingBar();
    }

    private void animateBackground() {
        if (imgSplashBackground == null) return;

        ObjectAnimator bgAlpha = ObjectAnimator.ofFloat(
                imgSplashBackground,
                "alpha",
                0f,
                0.78f
        );

        ObjectAnimator bgScaleX = ObjectAnimator.ofFloat(
                imgSplashBackground,
                "scaleX",
                1f,
                1.08f
        );

        ObjectAnimator bgScaleY = ObjectAnimator.ofFloat(
                imgSplashBackground,
                "scaleY",
                1f,
                1.08f
        );

        bgAlpha.setDuration(900);
        bgScaleX.setDuration(3200);
        bgScaleY.setDuration(3200);

        bgAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
        bgScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        bgScaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet bgSet = new AnimatorSet();
        bgSet.playTogether(bgAlpha, bgScaleX, bgScaleY);
        bgSet.start();
    }

    private void animateLogo() {
        if (imgSplashLogo == null) return;

        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(
                imgSplashLogo,
                "alpha",
                0f,
                1f
        );

        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(
                imgSplashLogo,
                "scaleX",
                0.75f,
                1.08f,
                1f
        );

        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(
                imgSplashLogo,
                "scaleY",
                0.75f,
                1.08f,
                1f
        );

        logoAlpha.setStartDelay(450);
        logoScaleX.setStartDelay(450);
        logoScaleY.setStartDelay(450);

        logoAlpha.setDuration(850);
        logoScaleX.setDuration(900);
        logoScaleY.setDuration(900);

        logoScaleX.setInterpolator(new OvershootInterpolator());
        logoScaleY.setInterpolator(new OvershootInterpolator());

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                logoAlpha,
                logoScaleX,
                logoScaleY
        );

        logoSet.start();
    }

    private void animateSplashText() {
        if (imgSplashText == null) return;

        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(
                imgSplashText,
                "alpha",
                0f,
                1f
        );

        ObjectAnimator textMove = ObjectAnimator.ofFloat(
                imgSplashText,
                "translationY",
                dpToPx(25),
                0f
        );

        textAlpha.setStartDelay(950);
        textMove.setStartDelay(950);

        textAlpha.setDuration(700);
        textMove.setDuration(700);

        textAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
        textMove.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet textSet = new AnimatorSet();
        textSet.playTogether(
                textAlpha,
                textMove
        );

        textSet.start();
    }

    private void animateLoadingBar() {
        if (viewLoadingLine == null) {
            moveToLogin();
            return;
        }

        viewLoadingLine.post(() -> {
            viewLoadingLine.getLayoutParams().width = 0;
            viewLoadingLine.requestLayout();

            ObjectAnimator loadingWidth = ObjectAnimator.ofInt(
                    new WidthWrapper(viewLoadingLine),
                    "width",
                    0,
                    dpToPx(170)
            );

            loadingWidth.setDuration(2400);
            loadingWidth.setStartDelay(650);
            loadingWidth.setInterpolator(new AccelerateDecelerateInterpolator());

            loadingWidth.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    moveToLogin();
                }
            });

            loadingWidth.start();
        });
    }

    private void moveToLogin() {
        if (alreadyMoveToLogin) return;

        alreadyMoveToLogin = true;

        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
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
