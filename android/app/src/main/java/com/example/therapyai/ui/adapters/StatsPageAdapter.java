package com.example.therapyai.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.StatsItem;

import java.util.List;

public class StatsPageAdapter extends RecyclerView.Adapter<StatsPageAdapter.StatsViewHolder> {
    private List<StatsItem> statsList;

    public StatsPageAdapter(List<StatsItem> statsList) {
        this.statsList = statsList;
    }

    @NonNull
    @Override
    public StatsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stat_slide, parent, false);
        return new StatsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatsViewHolder holder, int position) {
        StatsItem item = statsList.get(position);
        holder.titleText.setText(item.getTitle());
        holder.valueText.setText(item.getValue());
        holder.iconView.setImageResource(item.getIconResourceId());
    }

    @Override
    public int getItemCount() {
        return statsList.size();
    }

    static class StatsViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleText;
        TextView valueText;

        StatsViewHolder(View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.statIcon);
            titleText = itemView.findViewById(R.id.statTitle);
            valueText = itemView.findViewById(R.id.statValue);
        }
    }
}


