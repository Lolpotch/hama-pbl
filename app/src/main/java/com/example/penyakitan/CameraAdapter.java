package com.example.penyakitan;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_IMAGE = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private final List<CameraImage> imageList;
    private boolean showLoadAllFooter = false;
    private boolean loadAllLoading = false;
    private OnLoadAllClickListener onLoadAllClickListener;

    public CameraAdapter(List<CameraImage> imageList) {
        this.imageList = imageList;
    }

    public void setShowLoadAllFooter(boolean showLoadAllFooter) {
        this.showLoadAllFooter = showLoadAllFooter;
    }

    public void setLoadAllLoading(boolean loadAllLoading) {
        this.loadAllLoading = loadAllLoading;
    }

    public void setOnLoadAllClickListener(OnLoadAllClickListener listener) {
        this.onLoadAllClickListener = listener;
    }

    public boolean isFooterPosition(int position) {
        return showLoadAllFooter && position == imageList.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_FOOTER) {
            int horizontalPadding = dpToPx(parent, 16);
            int verticalPadding = dpToPx(parent, 12);

            TextView button = new TextView(parent.getContext());
            button.setTextColor(0xFFFFFFFF);
            button.setTextSize(14);
            button.setTypeface(null, Typeface.BOLD);
            button.setGravity(android.view.Gravity.CENTER);
            button.setBackgroundResource(R.drawable.bg_green_button);
            button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dpToPx(parent, 8), 0, dpToPx(parent, 8));
            button.setLayoutParams(params);

            return new FooterViewHolder(button);
        }

        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.item_camera_image, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        return isFooterPosition(position) ? VIEW_TYPE_FOOTER : VIEW_TYPE_IMAGE;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            FooterViewHolder footerHolder = (FooterViewHolder) holder;
            footerHolder.button.setText(loadAllLoading ? "Memuat..." : "Lihat Semua");
            footerHolder.button.setEnabled(!loadAllLoading);
            footerHolder.button.setOnClickListener(v -> {
                if (!loadAllLoading && onLoadAllClickListener != null) {
                    onLoadAllClickListener.onLoadAllClick();
                }
            });
            return;
        }

        ViewHolder imageHolder = (ViewHolder) holder;
        CameraImage image = imageList.get(position);

        imageHolder.tvFileName.setText(image.getFileName());
        imageHolder.tvDate.setText(image.getUpdated());

        Glide.with(imageHolder.itemView.getContext())
                .load(image.getImageUrl())
                .centerCrop()
                .placeholder(R.drawable.plant)
                .error(R.drawable.plant)
                .into(imageHolder.imgCamera);

        imageHolder.itemView.setOnClickListener(v -> {
            Context context = imageHolder.itemView.getContext();

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
        return imageList.size() + (showLoadAllFooter ? 1 : 0);
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

    public static class FooterViewHolder extends RecyclerView.ViewHolder {

        TextView button;

        public FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            button = (TextView) itemView;
        }
    }

    public interface OnLoadAllClickListener {
        void onLoadAllClick();
    }

    private int dpToPx(View view, int dp) {
        return (int) (dp * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
