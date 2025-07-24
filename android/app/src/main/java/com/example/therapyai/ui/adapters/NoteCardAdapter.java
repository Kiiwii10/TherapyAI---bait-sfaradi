package com.example.therapyai.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.NoteCard;

import java.util.ArrayList;
import java.util.List;

public class NoteCardAdapter extends RecyclerView.Adapter<NoteCardAdapter.NoteCardViewHolder> {

    private final List<NoteCard> noteCards;
    private final OnNoteCardClickListener listener;

    public interface OnNoteCardClickListener {
        void onNoteCardClick(NoteCard noteCard);
    }

    public NoteCardAdapter(List<NoteCard> noteCards, OnNoteCardClickListener listener) {
        this.noteCards = noteCards;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_card_editable,
                parent, false);
        return new NoteCardViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteCardViewHolder holder, int position) {
        NoteCard noteCard = noteCards.get(position);
        holder.bind(noteCard);

        // Make the whole card clickable
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNoteCardClick(noteCard);
            }
        });
    }

    @Override
    public int getItemCount() {
        return noteCards.size();
    }

    public void updateNotes(List<NoteCard> newNotes) {
        this.noteCards.clear();
        this.noteCards.addAll(newNotes);
        notifyDataSetChanged();
    }

    public List<NoteCard> getCurrentNotes() {
        return new ArrayList<>(noteCards); // Return a copy
    }

    static class NoteCardViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        TextView timestampTextView;
        NoteCard currentCard;

        public NoteCardViewHolder(@NonNull View itemView, OnNoteCardClickListener listener) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            descriptionTextView = itemView.findViewById(R.id.descriptionTextView);
            timestampTextView = itemView.findViewById(R.id.tvNoteTimestamp);

            itemView.setOnClickListener(v -> {
                if (listener != null && currentCard != null) {
                    listener.onNoteCardClick(currentCard);
                }
            });

        }

        void bind(NoteCard noteCard) {
            this.currentCard = noteCard; // Store reference
            titleTextView.setText(noteCard.getTitle());
            descriptionTextView.setText(noteCard.getContent());

            // Handle timestamp visibility/text
            if (noteCard.getTimestamp() != null && !noteCard.getTimestamp().isEmpty()) {
                timestampTextView.setText(noteCard.getTimestamp());
                timestampTextView.setVisibility(View.VISIBLE);
                // Adjust title if it includes timestamp placeholder
                if (noteCard.getTitle().contains("[%s]")) { // Or however you format it
                    titleTextView.setText(String.format(noteCard.getTitle().replace("[%s]", "[%s]"), noteCard.getTimestamp()));
                }
            } else {
                timestampTextView.setVisibility(View.GONE);
                // Ensure title doesn't show placeholder if no timestamp
                titleTextView.setText(noteCard.getTitle().replace(" [%s]", ""));
            }
            // Special handling for Summary title if needed
            if ("Summary".equals(noteCard.getTitle())) {
                titleTextView.setText(R.string.note_title_summary); // Use string resource
            }
        }
    }
}