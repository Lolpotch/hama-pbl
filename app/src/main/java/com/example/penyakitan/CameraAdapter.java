package com.example.penyakitan;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.ViewHolder> {

    private final List<CameraImage> imageList;

    public CameraAdapter(List<CameraImage> imageList) {
        this.imageList = imageList;
    }

    @NonNull
    @Override
    public CameraAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_camera_image, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CameraAdapter.ViewHolder holder, int position) {
        CameraImage image = imageList.get(position);

        holder.tvFileName.setText(image.getFileName());
        holder.tvDate.setText(image.getUpdated());

        Glide.with(holder.itemView.getContext())
                .load(image.getImageUrl())
                .centerCrop()
                .placeholder(R.drawable.plant)
                .error(R.drawable.plant)
                .into(holder.imgCamera);

        holder.itemView.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();

            Intent intent = new Intent(context, ImagePreviewActivity.class);
            intent.putExtra("image_url", image.getImageUrl());
            intent.putExtra("filename", image.getFileName());
            intent.putExtra("time", image.getUpdated());
            intent.putExtra("label", image.getLabel());
            intent.putExtra("source", image.getSource());

            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgCamera;
        TextView tvFileName, tvDate;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            imgCamera = itemView.findViewById(R.id.imgCamera);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}