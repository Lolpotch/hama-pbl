package com.example.penyakitan;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class LatestImageAdapter extends RecyclerView.Adapter<LatestImageAdapter.ViewHolder> {

    private final List<String> imageUrls;
    private final List<String> imageLabels;
    private final boolean tabletLayout;

    public LatestImageAdapter(List<String> imageUrls, List<String> imageLabels, boolean tabletLayout) {
        this.imageUrls = imageUrls;
        this.imageLabels = imageLabels;
        this.tabletLayout = tabletLayout;
    }

    @NonNull
    @Override
    public LatestImageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_latest_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LatestImageAdapter.ViewHolder holder, int position) {
        int firstImagePosition = holder.isTabletItem ? position * 2 : position;
        int secondImagePosition = firstImagePosition + 1;

        Glide.with(holder.itemView.getContext())
                .load(imageUrls.get(firstImagePosition))
                .centerCrop()
                .placeholder(R.drawable.plant)
                .error(R.drawable.plant)
                .into(holder.imgLatestItem);

        bindLabel(holder.tvLatestItemLabel, firstImagePosition);

        if (holder.isTabletItem && holder.containerLatestItemSecond != null && holder.imgLatestItemSecond != null) {
            if (secondImagePosition < imageUrls.size()) {
                holder.containerLatestItemSecond.setVisibility(View.VISIBLE);

                Glide.with(holder.itemView.getContext())
                        .load(imageUrls.get(secondImagePosition))
                        .centerCrop()
                        .placeholder(R.drawable.plant)
                        .error(R.drawable.plant)
                        .into(holder.imgLatestItemSecond);

                bindLabel(holder.tvLatestItemSecondLabel, secondImagePosition);
            } else {
                holder.containerLatestItemSecond.setVisibility(View.INVISIBLE);
                holder.imgLatestItemSecond.setImageResource(R.drawable.plant);
            }
        }
    }

    @Override
    public int getItemCount() {
        if (tabletLayout) {
            return (imageUrls.size() + 1) / 2;
        }

        return imageUrls.size();
    }

    private void bindLabel(TextView labelView, int imagePosition) {
        if (labelView == null) {
            return;
        }

        if (imagePosition >= 0 && imagePosition < imageLabels.size()) {
            labelView.setText(imageLabels.get(imagePosition));
        } else {
            labelView.setText("Foto Terbaru");
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgLatestItem;
        ImageView imgLatestItemSecond;
        TextView tvLatestItemLabel;
        TextView tvLatestItemSecondLabel;
        FrameLayout containerLatestItemSecond;
        boolean isTabletItem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgLatestItem = itemView.findViewById(R.id.imgLatestItem);
            imgLatestItemSecond = itemView.findViewById(R.id.imgLatestItemSecond);
            tvLatestItemLabel = itemView.findViewById(R.id.tvLatestItemLabel);
            tvLatestItemSecondLabel = itemView.findViewById(R.id.tvLatestItemSecondLabel);
            containerLatestItemSecond = itemView.findViewById(R.id.containerLatestItemSecond);
            isTabletItem = imgLatestItemSecond != null;
        }
    }
}
