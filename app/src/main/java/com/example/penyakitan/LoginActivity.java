package com.example.penyakitan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private static final String LOGIN_GUARD_PREFS = "login_guard";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_COOLDOWN_UNTIL = "cooldown_until";
    private static final int MAX_ATTEMPTS_BEFORE_COOLDOWN = 5;
    private static final long BASE_COOLDOWN_MS = 30_000L;
    private static final long MAX_COOLDOWN_MS = 15 * 60_000L;

    private FirebaseAuth auth;

    private EditText etUsername, etPassword;
    private TextView btnLogin;
    private ImageView btnTogglePassword;
    private boolean isPasswordVisible = false;
    private CountDownTimer cooldownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        updateLoginButtonForCooldown();

        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;

            if (isPasswordVisible) {
                etPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                );
                btnTogglePassword.setImageResource(R.drawable.eye);
            } else {
                etPassword.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD
                );
                btnTogglePassword.setImageResource(R.drawable.merem);
            }

            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {
            if (isLoginCoolingDown()) {
                showCooldownMessage();
                return;
            }

            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty()) {
                etUsername.setError("Email tidak boleh kosong");
                etUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError("Password tidak boleh kosong");
                etPassword.requestFocus();
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Loading...");

            auth.signInWithEmailAndPassword(username, password)
                    .addOnCompleteListener(task -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");

                        if (task.isSuccessful()) {
                            resetLoginGuard();

                            Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            registerFailedLoginAttempt();

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Email atau password salah",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
        });
    }

    @Override
    protected void onDestroy() {
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }

        super.onDestroy();
    }

    private SharedPreferences getLoginGuardPrefs() {
        return getSharedPreferences(LOGIN_GUARD_PREFS, MODE_PRIVATE);
    }

    private boolean isLoginCoolingDown() {
        long cooldownUntil = getLoginGuardPrefs().getLong(KEY_COOLDOWN_UNTIL, 0L);
        return cooldownUntil > SystemClock.elapsedRealtime();
    }

    private void registerFailedLoginAttempt() {
        SharedPreferences prefs = getLoginGuardPrefs();
        int failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;

        SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failedAttempts);

        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_COOLDOWN) {
            long cooldownMs = calculateCooldownMs(failedAttempts);
            long cooldownUntil = SystemClock.elapsedRealtime() + cooldownMs;

            editor.putLong(KEY_COOLDOWN_UNTIL, cooldownUntil);
        }

        editor.apply();
        updateLoginButtonForCooldown();
    }

    private long calculateCooldownMs(int failedAttempts) {
        int cooldownLevel = Math.max(
                0,
                failedAttempts - MAX_ATTEMPTS_BEFORE_COOLDOWN
        );

        long cooldownMs = BASE_COOLDOWN_MS;

        for (int i = 0; i < cooldownLevel; i++) {
            cooldownMs *= 2;

            if (cooldownMs >= MAX_COOLDOWN_MS) {
                return MAX_COOLDOWN_MS;
            }
        }

        return Math.min(cooldownMs, MAX_COOLDOWN_MS);
    }

    private void resetLoginGuard() {
        getLoginGuardPrefs()
                .edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_COOLDOWN_UNTIL)
                .apply();
    }

    private void updateLoginButtonForCooldown() {
        if (btnLogin == null) {
            return;
        }

        if (cooldownTimer != null) {
            cooldownTimer.cancel();
            cooldownTimer = null;
        }

        long cooldownUntil = getLoginGuardPrefs().getLong(KEY_COOLDOWN_UNTIL, 0L);
        long remainingMs = cooldownUntil - SystemClock.elapsedRealtime();

        if (remainingMs <= 0) {
            btnLogin.setEnabled(true);
            btnLogin.setText("Login");
            return;
        }

        btnLogin.setEnabled(false);
        updateCooldownButtonText(remainingMs);

        cooldownTimer = new CountDownTimer(remainingMs, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateCooldownButtonText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");
            }
        };

        cooldownTimer.start();
    }

    private void updateCooldownButtonText(long remainingMs) {
        long remainingSeconds = Math.max(1L, remainingMs / 1000L);
        btnLogin.setText("Coba lagi " + remainingSeconds + "s");
    }

    private void showCooldownMessage() {
        long cooldownUntil = getLoginGuardPrefs().getLong(KEY_COOLDOWN_UNTIL, 0L);
        long remainingSeconds = Math.max(
                1L,
                (cooldownUntil - SystemClock.elapsedRealtime()) / 1000L
        );

        Toast.makeText(
                this,
                "Terlalu banyak percobaan. Coba lagi dalam "
                        + remainingSeconds
                        + " detik.",
                Toast.LENGTH_SHORT
        ).show();
    }
}
