package com.example.penyakitan;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class CameraCaptureActivity extends AppCompatActivity {

    private static final String PREF_PENDING_UPLOAD = "pending_upload";
    private static final String KEY_PENDING_IMAGE_PATH = "pending_image_path";
    private static final String KEY_PENDING_FILENAME = "pending_filename";
    private static final String KEY_PENDING_SCAN_MODE = "pending_scan_mode";

    private final String functionUrl =
            "https://mobile-image-uploader-971770758012.asia-southeast2.run.app";

    private final String inferenceApiUrl =
            "https://lokasight-inference-api-971770758012.asia-southeast2.run.app/predict";

    private final String bucketPublicUrl =
            "https://storage.googleapis.com/lokasight/captures/";

    private static final int CUSTOM_SAMPLE_STEP = 2;
    private static final int CUSTOM_COLOR_BITS = 5;
    private static final int JPEG_OUTPUT_QUALITY = 100;

    private ImageView imgUploadPreview;
    private ProgressBar progressUpload;
    private TextView tvUploadStatus;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Uri capturedImageUri;
    private File capturedImageFile;
    private String localFilename;
    private String resultFilename;

    // Mode default kalau activity dibuka tanpa pilihan
    private String scanMode = "disease";

    private int lastHttpResponseCode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        readScanModeFromIntent();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openCameraFullResolution();
                    } else {
                        Toast.makeText(
                                this,
                                "Izin kamera ditolak",
                                Toast.LENGTH_SHORT
                        ).show();
                        finish();
                    }
                }
        );

        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (capturedImageFile != null && capturedImageFile.exists()) {
                            showUploadScreen(capturedImageUri);
                            uploadThenInference(capturedImageFile);
                        } else {
                            Toast.makeText(
                                    this,
                                    "File gambar tidak ditemukan",
                                    Toast.LENGTH_SHORT
                            ).show();
                            finish();
                        }
                    } else {
                        Toast.makeText(
                                this,
                                "Capture dibatalkan",
                                Toast.LENGTH_SHORT
                        ).show();

                        deleteTempImageIfExists();
                        finish();
                    }
                }
        );

        if (!resumePendingUploadFromIntent() && !resumePendingUploadFromStorage()) {
            checkCameraPermission();
        }
    }

    private void readScanModeFromIntent() {
        String modeFromIntent = getIntent().getStringExtra("scan_mode");

        if (modeFromIntent != null && !modeFromIntent.trim().isEmpty()) {
            scanMode = modeFromIntent.trim().toLowerCase();
        }

        if (!scanMode.equals("pest") && !scanMode.equals("disease")) {
            scanMode = "disease";
        }

        Log.d("SCAN_MODE", "Mode yang dipakai: " + scanMode);
    }

    private void showUploadScreen(Uri imageUri) {
        setContentView(R.layout.activity_camera_capture);

        imgUploadPreview = findViewById(R.id.imgUploadPreview);
        progressUpload = findViewById(R.id.progressUpload);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);

        imgUploadPreview.setImageURI(imageUri);
        progressUpload.setIndeterminate(true);

        if (scanMode.equals("pest")) {
            tvUploadStatus.setText("Foto sedang diproses untuk deteksi hama...");
        } else {
            tvUploadStatus.setText("Foto sedang diproses untuk deteksi penyakit...");
        }
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCameraFullResolution();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCameraFullResolution() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                capturedImageFile = createImageFile();

                capturedImageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        capturedImageFile
                );

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                cameraLauncher.launch(takePictureIntent);

            } catch (Exception e) {
                Log.e("CAMERA_ERROR", "Gagal membuka kamera: " + e.getMessage());

                Toast.makeText(
                        this,
                        "Gagal membuka kamera: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();

                finish();
            }
        } else {
            Toast.makeText(
                    this,
                    "Aplikasi kamera tidak ditemukan",
                    Toast.LENGTH_LONG
            ).show();
            finish();
        }
    }

    private File createImageFile() {
        long uploadedAt = System.currentTimeMillis() / 1000;

        if (scanMode.equals("pest")) {
            localFilename = "HP_PEST_" + uploadedAt + ".jpg";
        } else {
            localFilename = "HP_DISEASE_" + uploadedAt + ".jpg";
        }

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir == null) {
            storageDir = getFilesDir();
        }

        return new File(storageDir, localFilename);
    }

    private void uploadThenInference(File imageFile) {
        new Thread(() -> {
            final Uri finalImageUri = capturedImageUri;
            long totalStartMs = System.currentTimeMillis();

            try {
                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Mengompres foto untuk upload...");
                    }
                });

                if (localFilename == null || localFilename.trim().isEmpty()) {
                    localFilename = imageFile.getName();
                }

                long tokenStartMs = System.currentTimeMillis();
                String firebaseIdToken = getFirebaseIdToken();
                logDuration("firebase_token", tokenStartMs);

                long originalSize = imageFile.length();

                Log.d("IMAGE_COMPRESS", "Original filename: " + localFilename);
                Log.d("IMAGE_COMPRESS", "Original size bytes: " + originalSize);
                Log.d("IMAGE_COMPRESS", "Original size KB: " + (originalSize / 1024));

                long compressStartMs = System.currentTimeMillis();
                compressImageFileInPlace(imageFile);
                logDuration("compress_image", compressStartMs);

                long compressedSize = imageFile.length();

                Log.d("IMAGE_COMPRESS", "Custom sample step: " + CUSTOM_SAMPLE_STEP);
                Log.d("IMAGE_COMPRESS", "Custom color bits: " + CUSTOM_COLOR_BITS);
                Log.d("IMAGE_COMPRESS", "JPEG output quality: " + JPEG_OUTPUT_QUALITY);
                Log.d("IMAGE_COMPRESS", "Compressed size bytes: " + compressedSize);
                Log.d("IMAGE_COMPRESS", "Compressed size KB: " + (compressedSize / 1024));

                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Mengupload foto ke server...");
                    }
                });

                long uploadStartMs = System.currentTimeMillis();
                String uploadResponse = uploadImageFile(imageFile, firebaseIdToken);
                logDuration("upload_image", uploadStartMs);
                int uploadResponseCode = lastHttpResponseCode;

                Log.d("UPLOAD_RESPONSE", "Code: " + uploadResponseCode);
                Log.d("UPLOAD_RESPONSE", "Body length: " + safeLength(uploadResponse));

                if (uploadResponseCode != 200 && uploadResponseCode != 201) {
                    savePendingUpload(imageFile);
                    runOnUiThread(() -> {
                        if (tvUploadStatus != null) {
                            tvUploadStatus.setText("Upload gagal, membuka hasil...");
                        }

                        openDetectionResult(
                                finalImageUri,
                                uploadResponseCode,
                                uploadResponse
                        );
                    });

                    return;
                }

                String serverFilename = extractFilenameFromResponse(uploadResponse);

                if (serverFilename == null || serverFilename.trim().isEmpty()) {
                    serverFilename = localFilename;
                }
                resultFilename = serverFilename;

                String uploadedImageUrl = extractImageUrlFromResponse(uploadResponse);

                if (uploadedImageUrl == null || uploadedImageUrl.trim().isEmpty()) {
                    uploadedImageUrl = bucketPublicUrl + serverFilename;
                }

                Log.d("UPLOAD_IMAGE_URL", "Image URL tersedia: " + (!uploadedImageUrl.trim().isEmpty()));


                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        if (scanMode.equals("pest")) {
                            tvUploadStatus.setText("Upload berhasil, menjalankan deteksi hama...");
                        } else {
                            tvUploadStatus.setText("Upload berhasil, menjalankan deteksi penyakit...");
                        }
                    }
                });

                long inferenceStartMs = System.currentTimeMillis();
                String inferenceResponse = runInferenceFromImageUrl(uploadedImageUrl, firebaseIdToken);
                logDuration("inference_api", inferenceStartMs);
                int inferenceResponseCode = lastHttpResponseCode;

                Log.d("INFERENCE_RESPONSE", "Code: " + inferenceResponseCode);
                Log.d("INFERENCE_RESPONSE", "Body length: " + safeLength(inferenceResponse));
                logServerInferenceTiming(inferenceResponse);
                logDuration("total_upload_then_inference", totalStartMs);

                if (inferenceResponseCode == 200 || inferenceResponseCode == 201) {
                    clearPendingUpload();
                } else {
                    savePendingUpload(imageFile);
                }

                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        if (inferenceResponseCode == 200 || inferenceResponseCode == 201) {
                            tvUploadStatus.setText("Inference berhasil, membuka hasil...");
                        } else {
                            tvUploadStatus.setText("Inference gagal, membuka hasil...");
                        }
                    }

                    openDetectionResult(
                            finalImageUri,
                            inferenceResponseCode,
                            inferenceResponse
                    );
                });

            } catch (Exception e) {
                Log.e("UPLOAD_INFERENCE_ERROR", e.toString());
                savePendingUpload(imageFile);

                String errorJson = "{"
                        + "\"error\":\"Terjadi error saat upload atau inference\","
                        + "\"mode\":\"" + safeJsonText(scanMode) + "\","
                        + "\"detail\":\"" + safeJsonText(e.getMessage()) + "\""
                        + "}";

                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Terjadi error, membuka hasil...");
                    }

                    openDetectionResult(finalImageUri, 0, errorJson);
                });
            }
        }).start();
    }

    private String uploadImageFile(File imageFile, String firebaseIdToken) throws Exception {
        HttpURLConnection conn = null;

        try {
            String boundary = "----Boundary123456789";
            URL url = new URL(functionUrl);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            conn.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + boundary
            );
            addFirebaseAuthorizationHeader(conn, firebaseIdToken);

            OutputStream os = conn.getOutputStream();

            os.write(("--" + boundary + "\r\n").getBytes());

            os.write((
                    "Content-Disposition: form-data; " +
                            "name=\"file\"; " +
                            "filename=\"" + localFilename + "\"\r\n"
            ).getBytes());

            os.write("Content-Type: image/jpeg\r\n\r\n".getBytes());

            writeFileToOutputStream(imageFile, os);

            os.write("\r\n".getBytes());
            os.write(("--" + boundary + "--\r\n").getBytes());

            os.flush();
            os.close();

            lastHttpResponseCode = conn.getResponseCode();

            return readResponse(conn, lastHttpResponseCode);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String runInferenceFromImageUrl(String imageUrl, String firebaseIdToken) throws Exception {
        HttpURLConnection conn = null;

        try {
            String query = "?mode=" + URLEncoder.encode(scanMode, "UTF-8")
                    + "&source=mobile";

            URL url = new URL(inferenceApiUrl + query);

            Log.d("INFERENCE_URL", "Endpoint inference siap dipanggil");

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            conn.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
            );
            addFirebaseAuthorizationHeader(conn, firebaseIdToken);

            String formData = "image_url=" + URLEncoder.encode(
                    imageUrl,
                    "UTF-8"
            );

            OutputStream os = conn.getOutputStream();
            os.write(formData.getBytes("UTF-8"));
            os.flush();
            os.close();

            lastHttpResponseCode = conn.getResponseCode();

            return readResponse(conn, lastHttpResponseCode);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean resumePendingUploadFromIntent() {
        boolean retryPending = getIntent().getBooleanExtra("retry_pending_upload", false);
        String pendingPath = getIntent().getStringExtra("pending_image_path");

        if (!retryPending || pendingPath == null || pendingPath.trim().isEmpty()) {
            return false;
        }

        File pendingFile = new File(pendingPath);

        if (!pendingFile.exists()) {
            Toast.makeText(
                    this,
                    "Foto pending tidak ditemukan. Silakan ambil foto ulang.",
                    Toast.LENGTH_LONG
            ).show();
            clearPendingUpload();
            finish();
            return true;
        }

        capturedImageFile = pendingFile;
        capturedImageUri = Uri.fromFile(pendingFile);
        localFilename = getIntent().getStringExtra("filename");

        if (localFilename == null || localFilename.trim().isEmpty()) {
            localFilename = pendingFile.getName();
        }

        WorkManager.getInstance(this).cancelUniqueWork(PendingInferenceWorker.UNIQUE_WORK_NAME);
        showUploadScreen(capturedImageUri);
        tvUploadStatus.setText("Memproses ulang foto yang tersimpan...");
        uploadThenInference(capturedImageFile);
        return true;
    }

    private boolean resumePendingUploadFromStorage() {
        SharedPreferences prefs = getSharedPreferences(PREF_PENDING_UPLOAD, MODE_PRIVATE);
        String pendingPath = prefs.getString(KEY_PENDING_IMAGE_PATH, "");

        if (pendingPath == null || pendingPath.trim().isEmpty()) {
            return false;
        }

        File pendingFile = new File(pendingPath);

        if (!pendingFile.exists()) {
            clearPendingUpload();
            return false;
        }

        String pendingMode = prefs.getString(KEY_PENDING_SCAN_MODE, scanMode);

        if (pendingMode != null && !pendingMode.trim().isEmpty()) {
            scanMode = pendingMode;
        }

        capturedImageFile = pendingFile;
        capturedImageUri = Uri.fromFile(pendingFile);
        localFilename = prefs.getString(KEY_PENDING_FILENAME, pendingFile.getName());

        WorkManager.getInstance(this).cancelUniqueWork(PendingInferenceWorker.UNIQUE_WORK_NAME);
        showUploadScreen(capturedImageUri);
        tvUploadStatus.setText("Memproses foto pending yang tersimpan...");
        uploadThenInference(capturedImageFile);
        return true;
    }

    private String getFirebaseIdToken() throws Exception {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            throw new Exception("User belum login. Silakan login ulang.");
        }

        String token = Tasks.await(currentUser.getIdToken(false)).getToken();

        if (token == null || token.trim().isEmpty()) {
            throw new Exception("Token login Firebase tidak tersedia. Silakan login ulang.");
        }

        return token;
    }

    private void addFirebaseAuthorizationHeader(
            HttpURLConnection conn,
            String firebaseIdToken
    ) {
        conn.setRequestProperty(
                "Authorization",
                "Bearer " + firebaseIdToken
        );
    }

    private void compressImageFileInPlace(File imageFile) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());

        if (bitmap == null) {
            throw new Exception("Gagal decode gambar untuk kompresi");
        }

        Bitmap compressedBitmap = lossyCompressBitmap(
                bitmap,
                CUSTOM_SAMPLE_STEP,
                CUSTOM_COLOR_BITS
        );

        FileOutputStream fos = new FileOutputStream(imageFile, false);

        compressedBitmap.compress(
                Bitmap.CompressFormat.JPEG,
                JPEG_OUTPUT_QUALITY,
                fos
        );

        fos.flush();
        fos.close();

        bitmap.recycle();
        compressedBitmap.recycle();
    }

    private Bitmap lossyCompressBitmap(Bitmap original, int sampleStep, int colorBits) {
        int newWidth = Math.max(1, original.getWidth() / sampleStep);
        int newHeight = Math.max(1, original.getHeight() / sampleStep);

        Bitmap result = Bitmap.createBitmap(
                newWidth,
                newHeight,
                Bitmap.Config.ARGB_8888
        );

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int sourceX = Math.min(original.getWidth() - 1, x * sampleStep);
                int sourceY = Math.min(original.getHeight() - 1, y * sampleStep);
                int pixel = original.getPixel(sourceX, sourceY);

                result.setPixel(x, y, quantizeColor(pixel, colorBits));
            }
        }

        return result;
    }

    private int quantizeColor(int color, int bits) {
        int levels = 1 << bits;
        int shift = 8 - bits;

        int red = quantizeChannel(Color.red(color), shift, levels);
        int green = quantizeChannel(Color.green(color), shift, levels);
        int blue = quantizeChannel(Color.blue(color), shift, levels);

        return Color.rgb(red, green, blue);
    }

    private int quantizeChannel(int value, int shift, int levels) {
        return (value >> shift) * 255 / (levels - 1);
    }

    private void writeFileToOutputStream(File file, OutputStream outputStream) throws Exception {
        FileInputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        fis.close();
    }

    private String readResponse(HttpURLConnection conn, int responseCode) {
        try {
            InputStream inputStream;

            if (responseCode >= 200 && responseCode < 300) {
                inputStream = conn.getInputStream();
            } else {
                inputStream = conn.getErrorStream();
            }

            if (inputStream == null) {
                return "";
            }

            byte[] buffer = new byte[1024];
            StringBuilder builder = new StringBuilder();
            int length;

            while ((length = inputStream.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, length));
            }

            inputStream.close();
            return builder.toString();

        } catch (Exception e) {
            Log.e("READ_RESPONSE", "Gagal baca response: " + e.getMessage());
            return "";
        }
    }

    private String extractFilenameFromResponse(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                return "";
            }

            JSONObject json = new JSONObject(responseMessage);

            if (json.has("filename")) {
                return json.optString("filename", "");
            }

            JSONObject data = json.optJSONObject("data");
            if (data != null && data.has("filename")) {
                return data.optString("filename", "");
            }

            if (json.has("name")) {
                return json.optString("name", "");
            }

        } catch (Exception e) {
            Log.e("PARSE_RESPONSE", "Gagal ambil filename: " + e.getMessage());
        }

        return "";
    }

    private String extractImageUrlFromResponse(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                return "";
            }

            JSONObject json = new JSONObject(responseMessage);
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                JSONObject imageInfo = data.optJSONObject("image_info");

                if (imageInfo != null && imageInfo.has("image_url")) {
                    return imageInfo.optString("image_url", "");
                }
            }

            if (json.has("image")) {
                return json.optString("image", "");
            }

            if (json.has("image_url")) {
                return json.optString("image_url", "");
            }

            if (json.has("url")) {
                return json.optString("url", "");
            }

            if (json.has("public_url")) {
                return json.optString("public_url", "");
            }

            if (json.has("mediaLink")) {
                return json.optString("mediaLink", "");
            }

        } catch (Exception e) {
            Log.e("PARSE_RESPONSE", "Gagal parse image url: " + e.getMessage());
        }

        return "";
    }

    private String safeJsonText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private void logDuration(String stage, long startMs) {
        long durationMs = System.currentTimeMillis() - startMs;
        Log.d("INFERENCE_TIMING", stage + " ms: " + durationMs);
    }

    private void logServerInferenceTiming(String responseMessage) {
        try {
            if (responseMessage == null || responseMessage.trim().isEmpty()) {
                return;
            }

            JSONObject json = new JSONObject(responseMessage);

            if (json.has("processing_time_seconds")) {
                Log.d(
                        "INFERENCE_TIMING",
                        "server_processing ms: "
                                + Math.round(json.optDouble("processing_time_seconds", 0) * 1000)
                );
            }

            JSONObject performance = json.optJSONObject("performance");
            if (performance == null) {
                return;
            }

            JSONObject stageSeconds = performance.optJSONObject("stage_seconds");
            if (stageSeconds == null) {
                return;
            }

            String[] keys = {
                    "read_input_image_seconds",
                    "store_original_image_seconds",
                    "prediction_and_artifacts_seconds",
                    "store_result_json_seconds",
                    "firebase_sync_seconds"
            };

            for (String key : keys) {
                if (stageSeconds.has(key)) {
                    Log.d(
                            "INFERENCE_TIMING",
                            "server_" + key.replace("_seconds", "_ms") + ": "
                                    + Math.round(stageSeconds.optDouble(key, 0) * 1000)
                    );
                }
            }
        } catch (Exception e) {
            Log.d("INFERENCE_TIMING", "server_timing_parse_failed: " + e.getMessage());
        }
    }

    private void savePendingUpload(File imageFile) {
        try {
            if (imageFile == null || !imageFile.exists()) {
                return;
            }

            File pendingDir = new File(getFilesDir(), "pending_inference");

            if (!pendingDir.exists() && !pendingDir.mkdirs()) {
                return;
            }

            if (localFilename == null || localFilename.trim().isEmpty()) {
                localFilename = imageFile.getName();
            }

            File pendingFile = new File(pendingDir, localFilename);

            if (!imageFile.getAbsolutePath().equals(pendingFile.getAbsolutePath())) {
                copyFile(imageFile, pendingFile);
            }

            SharedPreferences prefs = getSharedPreferences(PREF_PENDING_UPLOAD, MODE_PRIVATE);
            prefs.edit()
                    .putString(KEY_PENDING_IMAGE_PATH, pendingFile.getAbsolutePath())
                    .putString(KEY_PENDING_FILENAME, localFilename)
                    .putString(KEY_PENDING_SCAN_MODE, scanMode)
                    .apply();

            capturedImageFile = pendingFile;
            capturedImageUri = Uri.fromFile(pendingFile);
            enqueuePendingInferenceWork();
        } catch (Exception e) {
            Log.e("PENDING_UPLOAD", "Gagal menyimpan pending upload: " + e.getMessage());
        }
    }

    private void clearPendingUpload() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREF_PENDING_UPLOAD, MODE_PRIVATE);
            String pendingPath = prefs.getString(KEY_PENDING_IMAGE_PATH, "");
            prefs.edit().clear().apply();

            if (pendingPath != null && !pendingPath.trim().isEmpty()) {
                File pendingFile = new File(pendingPath);
                File pendingDir = new File(getFilesDir(), "pending_inference");

                if (pendingFile.exists()
                        && pendingFile.getParentFile() != null
                        && pendingFile.getParentFile().equals(pendingDir)) {
                    pendingFile.delete();
                }
            }

            WorkManager.getInstance(this).cancelUniqueWork(PendingInferenceWorker.UNIQUE_WORK_NAME);
        } catch (Exception e) {
            Log.e("PENDING_UPLOAD", "Gagal membersihkan pending upload: " + e.getMessage());
        }
    }

    private void enqueuePendingInferenceWork() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest retryRequest = new OneTimeWorkRequest.Builder(PendingInferenceWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                )
                .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                PendingInferenceWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                retryRequest
        );
    }

    private void copyFile(File source, File target) throws Exception {
        FileInputStream inputStream = new FileInputStream(source);
        FileOutputStream outputStream = new FileOutputStream(target, false);

        byte[] buffer = new byte[8192];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    private void openDetectionResult(Uri imageUri, int responseCode, String responseMessage) {
        Intent intent = new Intent(
                CameraCaptureActivity.this,
                DetectionResultActivity.class
        );

        if (imageUri != null) {
            intent.putExtra("image_uri", imageUri.toString());
        }

        String filename = resultFilename;
        if (filename == null || filename.trim().isEmpty()) {
            filename = localFilename;
        }
        if (filename != null && !filename.trim().isEmpty()) {
            intent.putExtra("filename", filename);
        }

        intent.putExtra("responseCode", responseCode);
        intent.putExtra("responseMessage", responseMessage);
        intent.putExtra("scan_mode", scanMode);
        addPendingUploadExtras(intent);

        startActivity(intent);
        finish();
    }

    private void addPendingUploadExtras(Intent intent) {
        SharedPreferences prefs = getSharedPreferences(PREF_PENDING_UPLOAD, MODE_PRIVATE);
        String pendingPath = prefs.getString(KEY_PENDING_IMAGE_PATH, "");

        if (pendingPath == null || pendingPath.trim().isEmpty()) {
            return;
        }

        File pendingFile = new File(pendingPath);

        if (!pendingFile.exists()) {
            clearPendingUpload();
            return;
        }

        intent.putExtra("has_pending_upload", true);
        intent.putExtra("pending_image_path", pendingPath);
        intent.putExtra("pending_filename", prefs.getString(KEY_PENDING_FILENAME, localFilename));
        intent.putExtra("pending_scan_mode", prefs.getString(KEY_PENDING_SCAN_MODE, scanMode));
    }

    private void deleteTempImageIfExists() {
        try {
            if (capturedImageFile != null && capturedImageFile.exists()) {
                capturedImageFile.delete();
            }
        } catch (Exception e) {
            Log.e("DELETE_TEMP", "Gagal hapus file: " + e.getMessage());
        }
    }
}
