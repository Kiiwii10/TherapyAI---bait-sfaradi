package com.example.therapyai.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.PendingInboxItem;

import java.util.List;


public class ProcessedDataAdapter extends RecyclerView.Adapter<ProcessedDataAdapter.ViewHolder> {
    private List<PendingInboxItem> dataEntries;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PendingInboxItem data);
    }

    public ProcessedDataAdapter(List<PendingInboxItem> dataEntries, OnItemClickListener listener) {
        this.dataEntries = dataEntries;
        this.listener = listener;
    }

    public void updateData(List<PendingInboxItem> newData) {
        this.dataEntries = newData != null ? newData : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_processed_data, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PendingInboxItem data = dataEntries.get(position);

        holder.patientNameTextView.setText(data.getPatientName());
        holder.sessionDateTextView.setText(data.getSessionDate());
        holder.summaryPreviewTextView.setText(data.getSummaryPreview());

        holder.itemView.setOnClickListener(v -> listener.onItemClick(data));
    }

    @Override
    public int getItemCount() {
        return dataEntries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView patientNameTextView;
        TextView sessionDateTextView;
        TextView summaryPreviewTextView;

        ViewHolder(View itemView) {
            super(itemView);
            patientNameTextView = itemView.findViewById(R.id.patientNameTextView);
            sessionDateTextView = itemView.findViewById(R.id.sessionDateTextView);
            summaryPreviewTextView = itemView.findViewById(R.id.summaryPreviewTextView);
        }
    }
}