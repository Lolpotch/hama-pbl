package com.example.penyakitan;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class ImagePreviewActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private TextView tvPreviewTitle, tvPreviewFileName, tvPreviewTime;
    private TextView tvPreviewSource, tvPreviewPoint;
    private TextView btnClosePreview;

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
        btnClosePreview = findViewById(R.id.btnClosePreview);

        String imageUrl = getIntent().getStringExtra("image_url");
        String filename = getIntent().getStringExtra("filename");
        String time = getIntent().getStringExtra("time");
        String label = getIntent().getStringExtra("label");
        String source = getIntent().getStringExtra("source");

        tvPreviewTitle.setText("History Kamera");

        String finalSource = safeText(source, "-");
        String finalPoint = safeText(label, "-");

        if (finalPoint.equalsIgnoreCase("HP")) {
            finalPoint = "-";
        }

        tvPreviewFileName.setText("File: " + safeText(filename, "Tidak diketahui"));
        tvPreviewTime.setText("Waktu: " + safeText(time, "-"));
        tvPreviewSource.setText(finalSource);
        tvPreviewPoint.setText(finalPoint);

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
}