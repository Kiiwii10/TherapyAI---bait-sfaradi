package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.SessionSummary;
import java.util.ArrayList;
import java.util.List;

public class SessionSummaryAdapter extends RecyclerView.Adapter<SessionSummaryAdapter.SessionSummaryViewHolder> {

    private List<SessionSummary> sessionSummaries = new ArrayList<>();
    private final Context context;
    private final OnSessionSummaryClickListener listener;

    public interface OnSessionSummaryClickListener {
        void onSessionSummaryClick(SessionSummary sessionSummary);
    }

    public SessionSummaryAdapter(Context context, OnSessionSummaryClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setSessionSummaries(List<SessionSummary> summaries) {
        this.sessionSummaries = (summaries != null) ? new ArrayList<>(summaries) : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<SessionSummary> getSessionSummaries() {
        return sessionSummaries;
    }


    @NonNull
    @Override
    public SessionSummaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_session_summary, parent, false);
        return new SessionSummaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionSummaryViewHolder holder, int position) {
        SessionSummary item = sessionSummaries.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return sessionSummaries.size();
    }

    static class SessionSummaryViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionTitle, tvSessionDate, tvSessionDescription;

        SessionSummaryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSessionTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvSessionDate = itemView.findViewById(R.id.tvSessionDate);
            tvSessionDescription = itemView.findViewById(R.id.tvSessionDescription);
        }

        void bind(final SessionSummary summary, final OnSessionSummaryClickListener listener) {
            tvSessionTitle.setText(summary.getTitle());
            tvSessionDate.setText(summary.getDate());
            tvSessionDescription.setText(summary.getDescriptionPreview());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSessionSummaryClick(summary);
                }
            });
        }
    }
}