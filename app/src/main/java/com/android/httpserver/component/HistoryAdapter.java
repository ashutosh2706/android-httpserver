package com.android.httpserver.component;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.httpserver.R;
import com.android.httpserver.model.History;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(History history);
    }

    public interface OnItemClickListener {
        void onItemClick(History history);
    }

    private List<History> historyList = new ArrayList<>();
    private final OnDeleteClickListener deleteListener;
    private final OnItemClickListener itemClickListener;

    public HistoryAdapter(OnDeleteClickListener deleteListener, OnItemClickListener itemClickListener) {
        this.deleteListener = deleteListener;
        this.itemClickListener = itemClickListener;
    }

    public void setHistoryList(List<History> historyList) {
        this.historyList = historyList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView fileNameTextView, fileSizeTextView, subtitleTextView;
        ImageButton actionButton;

        public ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewThumb);
            fileNameTextView = itemView.findViewById(R.id.fileNameTextView);
            fileSizeTextView = itemView.findViewById(R.id.fileSizeTextView);
            subtitleTextView = itemView.findViewById(R.id.subtitleTextView);
            actionButton = itemView.findViewById(R.id.actionButton);
        }
    }

    @NonNull
    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryAdapter.ViewHolder holder, int position) {
        History item = historyList.get(position);
        holder.fileNameTextView.setText(item.getFileName());
        holder.fileSizeTextView.setText(item.getFileSize());
        holder.subtitleTextView.setText(item.getExtra());
        holder.imageView.setImageResource(item.getImageResId());
        holder.actionButton.setVisibility(View.VISIBLE);
        holder.actionButton.setOnClickListener(v -> deleteListener.onDeleteClick(item));
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

}
