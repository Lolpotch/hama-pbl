package com.example.penyakitan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class PendingInferenceWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "pending_inference_retry";

    private static final String PREF_PENDING_UPLOAD = "pending_upload";
    private static final String KEY_PENDING_IMAGE_PATH = "pending_image_path";
    private static final String KEY_PENDING_FILENAME = "pending_filename";
    private static final String KEY_PENDING_SCAN_MODE = "pending_scan_mode";

    private static final String FUNCTION_URL =
            "https://mobile-image-uploader-971770758012.asia-southeast2.run.app";
    private static final String INFERENCE_API_URL =
            "https://lokasight-inference-api-971770758012.asia-southeast2.run.app/predict";
    private static final String BUCKET_PUBLIC_URL =
            "https://storage.googleapis.com/lokasight/captures/";

    private int lastHttpResponseCode = 0;

    public PendingInferenceWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREF_PENDING_UPLOAD, Context.MODE_PRIVATE);

        String imagePath = prefs.getString(KEY_PENDING_IMAGE_PATH, "");
        String filename = prefs.getString(KEY_PENDING_FILENAME, "");
        String scanMode = prefs.getString(KEY_PENDING_SCAN_MODE, "disease");

        if (imagePath == null || imagePath.trim().isEmpty()) {
            return Result.success();
        }

        File imageFile = new File(imagePath);

        if (!imageFile.exists()) {
            clearPendingUpload(prefs, imagePath);
            return Result.success();
        }

        if (filename == null || filename.trim().isEmpty()) {
            filename = imageFile.getName();
        }

        try {
            String firebaseIdToken = getFirebaseIdToken();
            String uploadResponse = uploadImageFile(imageFile, filename, firebaseIdToken);

            if (lastHttpResponseCode < 200 || lastHttpResponseCode >= 300) {
                return shouldRetry(lastHttpResponseCode) ? Result.retry() : Result.failure();
            }

            String uploadedImageUrl = extractImageUrlFromResponse(uploadResponse);

            if (uploadedImageUrl == null || uploadedImageUrl.trim().isEmpty()) {
                String serverFilename = extractFilenameFromResponse(uploadResponse);
                if (serverFilename == null || serverFilename.trim().isEmpty()) {
                    serverFilename = filename;
                }
                uploadedImageUrl = BUCKET_PUBLIC_URL + serverFilename;
            }

            runInferenceFromImageUrl(uploadedImageUrl, scanMode, firebaseIdToken);

            if (lastHttpResponseCode >= 200 && lastHttpResponseCode < 300) {
                clearPendingUpload(prefs, imagePath);
                return Result.success();
            }

            return shouldRetry(lastHttpResponseCode) ? Result.retry() : Result.failure();

        } catch (Exception e) {
            return Result.retry();
        }
    }

    private String getFirebaseIdToken() throws Exception {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            throw new Exception("User belum login");
        }

        String token = Tasks.await(currentUser.getIdToken(false)).getToken();

        if (token == null || token.trim().isEmpty()) {
            throw new Exception("Token login tidak tersedia");
        }

        return token;
    }

    private String uploadImageFile(
            File imageFile,
            String filename,
            String firebaseIdToken
    ) throws Exception {
        HttpURLConnection conn = null;

        try {
            String boundary = "----Boundary123456789";
            URL url = new URL(FUNCTION_URL);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            addFirebaseAuthorizationHeader(conn, firebaseIdToken);

            OutputStream os = conn.getOutputStream();
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write((
                    "Content-Disposition: form-data; "
                            + "name=\"file\"; "
                            + "filename=\"" + filename + "\"\r\n"
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

    private String runInferenceFromImageUrl(
            String imageUrl,
            String scanMode,
            String firebaseIdToken
    ) throws Exception {
        HttpURLConnection conn = null;

        try {
            String query = "?mode=" + URLEncoder.encode(scanMode, "UTF-8")
                    + "&source=mobile";
            URL url = new URL(INFERENCE_API_URL + query);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            addFirebaseAuthorizationHeader(conn, firebaseIdToken);

            String formData = "image_url=" + URLEncoder.encode(imageUrl, "UTF-8");
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

    private void addFirebaseAuthorizationHeader(
            HttpURLConnection conn,
            String firebaseIdToken
    ) {
        conn.setRequestProperty("Authorization", "Bearer " + firebaseIdToken);
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
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

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

        } catch (Exception ignored) {
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

        } catch (Exception ignored) {
        }

        return "";
    }

    private boolean shouldRetry(int responseCode) {
        return responseCode == 0 || responseCode == 408 || responseCode >= 500;
    }

    private void clearPendingUpload(SharedPreferences prefs, String imagePath) {
        prefs.edit().clear().apply();

        if (imagePath == null || imagePath.trim().isEmpty()) {
            return;
        }

        File pendingFile = new File(imagePath);
        File pendingDir = new File(getApplicationContext().getFilesDir(), "pending_inference");

        if (pendingFile.exists()
                && pendingFile.getParentFile() != null
                && pendingFile.getParentFile().equals(pendingDir)) {
            pendingFile.delete();
        }
    }
}
