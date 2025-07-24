package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.TranscriptItem;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TranscriptAdapter extends RecyclerView.Adapter<TranscriptAdapter.ViewHolder> {

    private List<TranscriptItem> transcriptItems;
    private Context context;
    private final TranscriptChangeListener changeListener;

    public interface TranscriptChangeListener {
        void onTranscriptChanged();
    }

    public TranscriptAdapter(List<TranscriptItem> transcriptItems, Context context, TranscriptChangeListener listener) {
        this.transcriptItems = transcriptItems != null ? transcriptItems : new ArrayList<>();
        this.context = context;
        this.changeListener = listener;
    }

    public List<TranscriptItem> getCurrentData() {
        return transcriptItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transcript, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TranscriptItem item = transcriptItems.get(position);
        holder.speakerTextView.setText(item.getSpeaker());
        holder.timestampTextView.setText(formatTimestamp(item.getTimestamp()));

        // Text Editing
        if (holder.textWatcher != null) {
            holder.textEditText.removeTextChangedListener(holder.textWatcher);
        }
        holder.textEditText.setText(item.getText());
        holder.textWatcher = new MyTextWatcher(holder);
        holder.textEditText.addTextChangedListener(holder.textWatcher);

        View.OnClickListener speakerClickListener = v -> {
            int currentPosition = holder.getBindingAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                TranscriptItem currentItem = transcriptItems.get(currentPosition);
                String currentSpeaker = currentItem.getSpeaker();
                String newSpeaker = "Patient".equalsIgnoreCase(currentSpeaker) ? "Therapist" : "Patient";
                currentItem.setSpeaker(newSpeaker);
                holder.speakerTextView.setText(newSpeaker);
                if (changeListener != null) {
                    changeListener.onTranscriptChanged();
                }
            }
        };

        holder.speakerIcon.setOnClickListener(speakerClickListener);
        holder.speakerTextView.setOnClickListener(speakerClickListener);
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "--:--";
        }
        try {
            double secondsDouble = Double.parseDouble(timestamp);
            long totalSeconds = (long) secondsDouble;

            long hours = TimeUnit.SECONDS.toHours(totalSeconds);
            long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
            }
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }


    @Override
    public int getItemCount() {
        return transcriptItems.size();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < transcriptItems.size()) {
            transcriptItems.remove(position);
            notifyItemRemoved(position);
            if (changeListener != null) {
                changeListener.onTranscriptChanged();
            }
        }
    }

    public void insertItem(int position, TranscriptItem item) {
        if (position >= 0 && position <= transcriptItems.size() && item != null) {
            transcriptItems.add(position, item);
            notifyItemInserted(position);
            if (changeListener != null) {
                changeListener.onTranscriptChanged();
            }
        }
    }

//    public void updateData(List<TranscriptItem> newData) {
//        boolean listChanged = !areListsEqual(this.transcriptItems, newData);
//        if (listChanged) {
//            this.transcriptItems.clear();
//            if (newData != null) {
//                this.transcriptItems.addAll(newData);
//            }
//            notifyDataSetChanged();
//            if (changeListener != null) {
//                changeListener.onTranscriptChanged();
//            }
//        }
//    }

    public void updateData(List<TranscriptItem> newData) {
        this.transcriptItems.clear();
        if (newData != null) {
            this.transcriptItems.addAll(new ArrayList<>(newData));
        }
        notifyDataSetChanged();

        if (changeListener != null) {
            changeListener.onTranscriptChanged();
        }
    }



    private boolean areListsEqual(List<TranscriptItem> list1, List<TranscriptItem> list2) {
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list1.size(); i++) {
            TranscriptItem item1 = list1.get(i);
            TranscriptItem item2 = list2.get(i);
            if (!Objects.equals(item1.getText(), item2.getText()) || !Objects.equals(item1.getSpeaker(), item2.getSpeaker())) {
                return false;
            }
        }
        return true;
    }

    public boolean hasChanges() {
        for (TranscriptItem item : transcriptItems) {
            if (item.hasChanged()) {
                return true;
            }
        }
        return false;
    }



    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView speakerTextView;
        EditText textEditText;
        TextView timestampTextView;
        ImageView speakerIcon;
        MyTextWatcher textWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            speakerTextView = itemView.findViewById(R.id.speakerTextView);
            textEditText = itemView.findViewById(R.id.textEditText);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            speakerIcon = itemView.findViewById(R.id.speakerIcon);
        }
    }



    private class MyTextWatcher implements TextWatcher {
        private ViewHolder holder;

        MyTextWatcher(ViewHolder holder) {
            this.holder = holder;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position < transcriptItems.size()) {
                transcriptItems.get(position).setText(s.toString());
                if (changeListener != null) {
                    changeListener.onTranscriptChanged();
                }
            }
        }
    }
}
