package com.example.penyakitan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class CameraCaptureActivity extends AppCompatActivity {

    // =========================
    // API UPLOAD FOTO HP KE GCS
    // =========================
    private final String functionUrl =
            "https://mobile-camera-upload-picture-990423897913.europe-west1.run.app";

    // =========================
    // API ML INFERENCE
    // GANTI URL INI DENGAN URL CLOUD RUN ML KAMU
    // Harus endpoint /predict
    // =========================
    private final String inferenceApiUrl =
            "https://lokasight-inference-api-990423897913.asia-southeast2.run.app/predict";

    // =========================
    // FALLBACK PUBLIC URL
    // Dipakai kalau response upload tidak mengirim image_url
    // =========================
    private final String bucketPublicUrl =
            "https://storage.googleapis.com/camerahama-test/mobile-captures/";

    private static final int JPEG_UPLOAD_QUALITY = 85;

    private ImageView imgUploadPreview;
    private ProgressBar progressUpload;
    private TextView tvUploadStatus;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;

    private Uri capturedImageUri;
    private File capturedImageFile;
    private String localFilename;

    private int lastHttpResponseCode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        checkCameraPermission();
    }

    private void showUploadScreen(Uri imageUri) {
        setContentView(R.layout.activity_camera_capture);

        imgUploadPreview = findViewById(R.id.imgUploadPreview);
        progressUpload = findViewById(R.id.progressUpload);
        tvUploadStatus = findViewById(R.id.tvUploadStatus);

        imgUploadPreview.setImageURI(imageUri);
        progressUpload.setIndeterminate(true);
        tvUploadStatus.setText("Foto sedang diproses...");
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
        localFilename = "HP_" + uploadedAt + ".jpg";

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (storageDir == null) {
            storageDir = getFilesDir();
        }

        return new File(storageDir, localFilename);
    }

    private void uploadThenInference(File imageFile) {
        new Thread(() -> {
            final Uri finalImageUri = capturedImageUri;

            try {
                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Mengompres foto untuk upload...");
                    }
                });

                if (localFilename == null || localFilename.trim().isEmpty()) {
                    localFilename = imageFile.getName();
                }

                long originalSize = imageFile.length();

                Log.d("IMAGE_COMPRESS", "Original filename: " + localFilename);
                Log.d("IMAGE_COMPRESS", "Original size bytes: " + originalSize);
                Log.d("IMAGE_COMPRESS", "Original size KB: " + (originalSize / 1024));

                compressImageFileInPlace(imageFile, JPEG_UPLOAD_QUALITY);

                long compressedSize = imageFile.length();

                Log.d("IMAGE_COMPRESS", "Compressed quality: " + JPEG_UPLOAD_QUALITY);
                Log.d("IMAGE_COMPRESS", "Compressed size bytes: " + compressedSize);
                Log.d("IMAGE_COMPRESS", "Compressed size KB: " + (compressedSize / 1024));

                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Mengupload foto ke server...");
                    }
                });

                String uploadResponse = uploadImageFile(imageFile);
                int uploadResponseCode = lastHttpResponseCode;

                Log.d("UPLOAD_RESPONSE", "Code: " + uploadResponseCode);
                Log.d("UPLOAD_RESPONSE", "Response: " + uploadResponse);

                if (uploadResponseCode != 200 && uploadResponseCode != 201) {
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

                String uploadedImageUrl = extractImageUrlFromResponse(uploadResponse);

                if (uploadedImageUrl == null || uploadedImageUrl.trim().isEmpty()) {
                    uploadedImageUrl = bucketPublicUrl + serverFilename;
                }

                Log.d("UPLOAD_IMAGE_URL", uploadedImageUrl);

                runOnUiThread(() -> {
                    if (tvUploadStatus != null) {
                        tvUploadStatus.setText("Upload berhasil, menjalankan inference...");
                    }
                });

                String inferenceResponse = runInferenceFromImageUrl(uploadedImageUrl);
                int inferenceResponseCode = lastHttpResponseCode;

                Log.d("INFERENCE_RESPONSE", "Code: " + inferenceResponseCode);
                Log.d("INFERENCE_RESPONSE", "Response: " + inferenceResponse);

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

                String errorJson = "{"
                        + "\"error\":\"Terjadi error saat upload atau inference\","
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

    private String uploadImageFile(File imageFile) throws Exception {
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

    private String runInferenceFromImageUrl(String imageUrl) throws Exception {
        HttpURLConnection conn = null;

        try {
            /*
             * Untuk HP/mobile pakai:
             * mode=auto  -> disease + pest
             * source=mobile -> supaya tidak dipaksa disease saja
             */
            String query = "?mode=auto&source=mobile";
            URL url = new URL(inferenceApiUrl + query);

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

    private void compressImageFileInPlace(File imageFile, int quality) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap = BitmapFactory.decodeFile(
                imageFile.getAbsolutePath(),
                options
        );

        if (bitmap == null) {
            throw new Exception("Gagal decode gambar untuk kompresi");
        }

        FileOutputStream fos = new FileOutputStream(imageFile, false);

        bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                quality,
                fos
        );

        fos.flush();
        fos.close();

        bitmap.recycle();
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

    private void openDetectionResult(Uri imageUri, int responseCode, String responseMessage) {
        Intent intent = new Intent(
                CameraCaptureActivity.this,
                DetectionResultActivity.class
        );

        if (imageUri != null) {
            intent.putExtra("image_uri", imageUri.toString());
        }

        intent.putExtra("responseCode", responseCode);
        intent.putExtra("responseMessage", responseMessage);

        startActivity(intent);
        finish();
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