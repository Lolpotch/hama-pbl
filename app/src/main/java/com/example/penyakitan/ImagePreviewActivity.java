package com.example.penyakitan;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class ImagePreviewActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private TextView tvPreviewTitle, tvPreviewFileName, tvPreviewTime;
    private TextView tvPreviewSource, tvPreviewPoint;
    private View cardPreviewPoint;
    private ImageView btnClosePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        imgPreview = findViewById(R.id.imgPreview);
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle);
        tvPreviewFileName = findViewById(R.id.tvPreviewFileName);
        tvPreviewTime = findViewById(R.id.tvPreviewTime);
        tvPreviewSource = findViewById(R.id.tvPreviewSource);
        tvPreviewPoint = findViewById(R.id.tvPreviewPoint);
        cardPreviewPoint = findViewById(R.id.cardPreviewPoint);
        btnClosePreview = findViewById(R.id.btnClosePreview);

        String imageUrl = getIntent().getStringExtra("image_url");
        String filename = getIntent().getStringExtra("filename");
        String time = getIntent().getStringExtra("time");
        String label = getIntent().getStringExtra("label");
        String source = getIntent().getStringExtra("source");

        tvPreviewTitle.setText("History Kamera");

        String finalSource = safeText(source, "-");
        String finalPoint = safeText(label, "-");

        boolean isMobileSource = finalSource.equalsIgnoreCase("mobile")
                || finalSource.equalsIgnoreCase("hp");

        if (finalPoint.equalsIgnoreCase("HP")) {
            finalPoint = "-";
        }

        if (isGenericCameraFilename(filename)) {
            filename = getFileNameFromImageUrl(imageUrl);
        }

        tvPreviewFileName.setText("File: " + safeText(filename, "Tidak diketahui"));
        tvPreviewTime.setText("Waktu: " + safeText(time, "-"));
        tvPreviewSource.setText(isMobileSource ? "Mobile" : finalSource);
        tvPreviewPoint.setText(finalPoint);

        if (cardPreviewPoint != null) {
            cardPreviewPoint.setVisibility(isMobileSource ? View.GONE : View.VISIBLE);
        }

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .fitCenter()
                    .placeholder(R.drawable.plant)
                    .error(R.drawable.plant)
                    .into(imgPreview);
        } else {
            imgPreview.setImageResource(R.drawable.plant);
        }

        btnClosePreview.setOnClickListener(v -> finish());
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value;
    }

    private boolean isGenericCameraFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return true;
        }

        return filename.trim().equalsIgnoreCase("Foto Kamera");
    }

    private String getFileNameFromImageUrl(String imageUrl) {
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

        return cleanUrl.trim();
    }
}
