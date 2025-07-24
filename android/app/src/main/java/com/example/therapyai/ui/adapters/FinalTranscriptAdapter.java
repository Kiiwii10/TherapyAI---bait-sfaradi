package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.SentimentScore;
import com.example.therapyai.data.local.models.TimedNote;
import com.example.therapyai.data.local.models.FinalTranscriptEntry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import android.os.Handler;
import android.os.Looper;


public class FinalTranscriptAdapter extends RecyclerView.Adapter<FinalTranscriptAdapter.TranscriptViewHolder> {

    private List<FinalTranscriptEntry> transcriptEntries = new ArrayList<>();
    private List<TimedNote> timedNotes = new ArrayList<>();
    private final Map<FinalTranscriptEntry, List<TimedNote>> entryToNotesMap = new HashMap<>();
    private final Context context;
    private OnTranscriptItemClickListener listener; // Listener not currently used but kept

    private int highlightedPosition = RecyclerView.NO_POSITION;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private Runnable clearHighlightRunnable = null;
    private static final long HIGHLIGHT_DURATION_MS = 1000;

    @ColorInt private final int therapistColor;
    @ColorInt private final int patientColor; // Primary color for patient label
    @ColorInt private final int positiveSentimentColor;
    @ColorInt private final int neutralSentimentColor;
    @ColorInt private final int negativeSentimentColor;
    @ColorInt private final int mixedSentimentColor;
    @ColorInt private final int defaultSpeakerColor;


    public interface OnTranscriptItemClickListener {
        void onSentenceClick(FinalTranscriptEntry entry, boolean isPatientSentence); // isPatientSentence might be less relevant now
    }

    public FinalTranscriptAdapter(Context context, OnTranscriptItemClickListener listener) {
        this.context = context;
        this.listener = listener;

        // Resolve attribute colors
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true);
        therapistColor = typedValue.data;
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        patientColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.positiveSentimentColor, typedValue, true);
        positiveSentimentColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.neutralSentimentColor, typedValue, true);
        neutralSentimentColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.negativeSentimentColor, typedValue, true);
        negativeSentimentColor = typedValue.data;
        context.getTheme().resolveAttribute(R.attr.mixedSentimentColor, typedValue, true);
        mixedSentimentColor = typedValue.data;
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true);
        defaultSpeakerColor = typedValue.data;

    }


//    public void setData(List<FinalTranscriptEntry> entries, List<TimedNote> notes) {
//        int oldHighlight = highlightedPosition;
//        if (clearHighlightRunnable != null) {
//            highlightHandler.removeCallbacks(clearHighlightRunnable);
//            clearHighlightRunnable = null;
//        }
//        highlightedPosition = RecyclerView.NO_POSITION;
//        if (oldHighlight != RecyclerView.NO_POSITION && oldHighlight < getItemCount()) {
//            notifyItemChanged(oldHighlight, "CLEAR_HIGHLIGHT");
//        }
//
//        this.transcriptEntries = (entries != null) ? new ArrayList<>(entries) : new ArrayList<>();
//        this.timedNotes = (notes != null) ? new ArrayList<>(notes) : new ArrayList<>();
//        notifyDataSetChanged();
//    }


    public void setData(List<FinalTranscriptEntry> entries, List<TimedNote> notes) {
        int oldHighlight = highlightedPosition;
        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
            clearHighlightRunnable = null;
        }
        highlightedPosition = RecyclerView.NO_POSITION;
        if (oldHighlight != RecyclerView.NO_POSITION && oldHighlight < getItemCount()) {
            notifyItemChanged(oldHighlight, "CLEAR_HIGHLIGHT");
        }

        this.transcriptEntries = (entries != null) ? new ArrayList<>(entries) : new ArrayList<>();
        List<TimedNote> timedNotes = (notes != null) ? new ArrayList<>(notes) : new ArrayList<>();
        entryToNotesMap.clear();

        // If there are no entries or notes, just update the UI and return.
        if (this.transcriptEntries.isEmpty() || timedNotes.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Filter to get only patient entries, as they are the only ones notes can be attached to.
        List<FinalTranscriptEntry> patientEntries = this.transcriptEntries.stream()
                .filter(e -> "Patient".equalsIgnoreCase(e.getSpeaker()))
                .collect(Collectors.toList());

        if (patientEntries.isEmpty()) {
            notifyDataSetChanged(); // No patient entries to attach notes to.
            return;
        }

        // For each note, find the correct patient entry to attach it to.
        for (TimedNote note : timedNotes) {
            float noteTime = parseTimeToSeconds(note.getTime());
            if (noteTime < 0) continue; // Skip notes with invalid timestamps.

            FinalTranscriptEntry bestMatchEntry = null;
            float bestMatchTime = -1f;

            // Find the patient entry with the latest timestamp that is less than or equal to the note's time.
            for (FinalTranscriptEntry patientEntry : patientEntries) {
                float entryTime = parseTimeToSeconds(patientEntry.getTimestamp());
                if (entryTime >= 0 && entryTime <= noteTime) {
                    if (entryTime > bestMatchTime) {
                        bestMatchTime = entryTime;
                        bestMatchEntry = patientEntry;
                    }
                }
            }

            // If a suitable entry was found, associate the note with it.
            if (bestMatchEntry != null) {
                entryToNotesMap.computeIfAbsent(bestMatchEntry, k -> new ArrayList<>()).add(note);
            }
        }

        // (Optional) Sort notes within each entry's list by time for consistent display order.
        for (List<TimedNote> noteList : entryToNotesMap.values()) {
            noteList.sort(Comparator.comparing(n -> parseTimeToSeconds(n.getTime())));
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TranscriptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_transcript_entry, parent, false);
        return new TranscriptViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull TranscriptViewHolder holder,
                                 int position) {
        FinalTranscriptEntry entry = transcriptEntries.get(position);
        List<TimedNote> relevantNotes = entryToNotesMap.get(entry);
        boolean shouldHighlight = (position == highlightedPosition);
        holder.bind(entry, relevantNotes, listener, shouldHighlight,
                therapistColor, patientColor, positiveSentimentColor, neutralSentimentColor, negativeSentimentColor, mixedSentimentColor, defaultSpeakerColor);
    }


    @Override
    public void onBindViewHolder(@NonNull TranscriptViewHolder holder,
                                 int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            boolean highlightChanged = false;
            for (Object payload : payloads) {
                if ("SET_HIGHLIGHT".equals(payload)) {
                    holder.setHighlightState(true);
                    highlightChanged = true;
                } else if ("CLEAR_HIGHLIGHT".equals(payload)) {
                    holder.setHighlightState(false);
                    highlightChanged = true;
                }
            }
            if (!highlightChanged) {
                onBindViewHolder(holder, position);
            }
        }
    }


    @Override
    public int getItemCount() {
        return transcriptEntries.size();
    }

//    private List<TimedNote> findNotesForEntry(FinalTranscriptEntry entry) {
//        if (timedNotes.isEmpty() || entry.getTimestamp() == null || entry.getTimestamp().isEmpty()) {
//            return new ArrayList<>();
//        }
//        float entryTimeSeconds = parseTimeToSeconds(entry.getTimestamp());
//        if (entryTimeSeconds < 0) return new ArrayList<>();
//
//        // Define a tolerance window for matching notes (e.g., notes within +/- 2 seconds of the utterance)
//        // Or, if a note is considered part of an utterance if it starts at the same time.
//        // For simplicity, let's match notes that are very close in time.
//        // This tolerance might need adjustment based on how notes are logged.
//        float toleranceSeconds = 1.0f; // Note must be within 1 second of utterance start
//
//        return timedNotes.stream()
//                .filter(note -> {
//                    float noteTime = note.getTimeInSeconds();
//                    return Math.abs(noteTime - entryTimeSeconds) <= toleranceSeconds;
//                })
//                .collect(Collectors.toList());
//    }

    private float parseTimeToSeconds(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) return -1f;
            // Handle formats: HH:mm:ss.SSS, mm:ss.SSS, ss.SSS
            String[] parts = timeStr.split(":");
            if (parts.length == 3) { // HH:mm:ss.SSS or HH:mm:ss
                return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Float.parseFloat(parts[2]);
            } else if (parts.length == 2) { // mm:ss.SSS or mm:ss
                return Integer.parseInt(parts[0]) * 60 + Float.parseFloat(parts[1]);
            } else if (parts.length == 1) { // ss.SSS or ss
                return Float.parseFloat(parts[0]);
            } else {
                return -1f;
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.w("FinalTranscriptAdapter", "Failed to parse time: " + timeStr, e);
            return -1f;
        }
    }


    public void setHighlightPosition(int position) {
        if (position < 0 || position >= getItemCount()) {
            // If out of bounds, effectively clear highlight.
            // Only do this if the intention is to clear on invalid input.
            // Otherwise, just ignore out-of-bounds. For now, let's clear.
            if (highlightedPosition != RecyclerView.NO_POSITION) {
                if (clearHighlightRunnable != null) highlightHandler.removeCallbacks(clearHighlightRunnable);
                int prev = highlightedPosition;
                highlightedPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(prev, "CLEAR_HIGHLIGHT");
                clearHighlightRunnable = null;
            }
            return;
        }

        if (highlightedPosition == position) return;

        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
            clearHighlightRunnable = null;
        }

        int previousHighlight = highlightedPosition;
        highlightedPosition = position;

        if (previousHighlight != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousHighlight, "CLEAR_HIGHLIGHT");
        }
        if (highlightedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(highlightedPosition, "SET_HIGHLIGHT");

            clearHighlightRunnable = () -> {
                int currentHighlight = highlightedPosition; // capture current state
                if (currentHighlight != RecyclerView.NO_POSITION) {
                    highlightedPosition = RecyclerView.NO_POSITION;
                    notifyItemChanged(currentHighlight, "CLEAR_HIGHLIGHT");
                }
                clearHighlightRunnable = null;
            };
            highlightHandler.postDelayed(clearHighlightRunnable, HIGHLIGHT_DURATION_MS);
        }
    }


    static class TranscriptViewHolder extends RecyclerView.ViewHolder {
        TextView tvSpeakerLabel, tvSpeakerTime, tvSpeakerSentence;
        LinearLayout timedNotesContainer;
        View sentimentIndicatorVertical;
        View rootView;

        TranscriptViewHolder(@NonNull View itemView) {
            super(itemView);
            rootView = itemView;
            sentimentIndicatorVertical = itemView.findViewById(R.id.viewSentimentIndicatorVertical);
            tvSpeakerLabel = itemView.findViewById(R.id.tvSpeakerLabel);
            tvSpeakerTime = itemView.findViewById(R.id.tvSpeakerTime);
            tvSpeakerSentence = itemView.findViewById(R.id.tvSpeakerSentence);
            timedNotesContainer = itemView.findViewById(R.id.llTranscriptTimedNotesContainer);
        }

        void setHighlightState(boolean highlighted) {
            Log.d("AdapterHighlight", "Setting highlight state for pos " + getBindingAdapterPosition() + " to " + highlighted);
            rootView.setActivated(highlighted); // selector_transcript_highlight will handle this
        }

        // MODIFIED bind method
        void bind(FinalTranscriptEntry entry, List<TimedNote> notes,
                  OnTranscriptItemClickListener listener,
                  boolean isHighlighted,
                  @ColorInt int therapistColor, @ColorInt int patientColor,
                  @ColorInt int positiveSentimentColor, @ColorInt int neutralSentimentColor,
                  @ColorInt int negativeSentimentColor, @ColorInt int mixedSentimentColor,
                  @ColorInt int defaultSpeakerColor) {

            setHighlightState(isHighlighted);

            String speaker = entry.getSpeaker();
            tvSpeakerLabel.setText(speaker);
            tvSpeakerSentence.setText(entry.getText());
            tvSpeakerTime.setText(formatTime(entry.getTimestamp()));

            if ("Therapist".equalsIgnoreCase(speaker)) {
                tvSpeakerLabel.setTextColor(therapistColor);
                sentimentIndicatorVertical.setBackgroundColor(therapistColor); // Blue for therapist
                sentimentIndicatorVertical.setVisibility(View.VISIBLE);
            } else if ("Patient".equalsIgnoreCase(speaker)) {
                tvSpeakerLabel.setTextColor(patientColor);
                SentimentScore score = entry.getSentimentScore();
                if (score != null) {
                    sentimentIndicatorVertical.setBackgroundColor(getSentimentColor(score, positiveSentimentColor, neutralSentimentColor, negativeSentimentColor, mixedSentimentColor));
                    sentimentIndicatorVertical.setVisibility(View.VISIBLE);
                } else {
                    sentimentIndicatorVertical.setVisibility(View.GONE); // Or a default color
                }
            } else { // Other speakers if any
                tvSpeakerLabel.setTextColor(defaultSpeakerColor);
                sentimentIndicatorVertical.setVisibility(View.GONE); // Or a default color
            }

            // itemView.setOnClickListener(v -> { // example usage of listener
            //     if(listener != null) listener.onSentenceClick(entry, "Patient".equalsIgnoreCase(speaker));
            // });

            // Populate Timed Notes
            timedNotesContainer.removeAllViews(); // Clear previous notes
            if (notes != null && !notes.isEmpty()) {
                timedNotesContainer.setVisibility(View.VISIBLE);
                LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
                for (TimedNote note : notes) {
                    View noteView = inflater.inflate(R.layout.item_timed_note, timedNotesContainer, false);
                    TextView tvNoteTime = noteView.findViewById(R.id.tvTimedNoteTime);
                    TextView tvNoteText = noteView.findViewById(R.id.tvTimedNoteText);
                    if (tvNoteTime != null && tvNoteText != null) {
                        tvNoteTime.setText(formatTime(note.getTime()));
                        tvNoteText.setText(note.getNote());
                        timedNotesContainer.addView(noteView);
                    }
                }
            } else {
                timedNotesContainer.setVisibility(View.GONE);
            }
        }

        private float parseTimeToSeconds(String timeStr) { // Duplicated from adapter, consider utility class
            try {
                if (timeStr == null || timeStr.isEmpty()) return -1f;
                String[] parts = timeStr.split(":");
                if (parts.length == 3) {
                    return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Float.parseFloat(parts[2]);
                } else if (parts.length == 2) {
                    return Integer.parseInt(parts[0]) * 60 + Float.parseFloat(parts[1]);
                } else if (parts.length == 1) {
                    return Float.parseFloat(parts[0]);
                } else {
                    return -1f;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return -1f;
            }
        }

        private String formatTime(String timeStr) {
            float seconds = parseTimeToSeconds(timeStr);
            if (seconds < 0) return (timeStr != null && !timeStr.isEmpty()) ? timeStr : "--:--"; // Show original if parsing failed but not empty
            int minutes = (int) (seconds / 60);
            int secsPart = (int) (seconds % 60);
            // float millisPart = seconds - (minutes * 60) - secsPart; // if we need milliseconds
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secsPart);
        }

        private int getSentimentColor(SentimentScore score, @ColorInt int positiveColor, @ColorInt int neutralColor, @ColorInt int negativeColor, @ColorInt int mixedColor) {
            float pos = score.getPositive();
            float neg = score.getNegative();
            float neu = score.getNeutral();

            // Threshold to determine dominance
            float threshold = 0.05f; // e.g. needs to be at least 5% greater

            if (neg > pos + threshold && neg > neu + threshold) return negativeColor; // Clearly Negative
            if (pos > neg + threshold && pos > neu + threshold) return positiveColor; // Clearly Positive

            // If pos and neg are close, and both significantly higher than neutral
            if (Math.abs(pos - neg) < threshold && pos > neu + threshold && neg > neu + threshold) return mixedColor; // Mixed

            // If neutral is dominant or all are close
            return neutralColor; // Default to Neutral
        }
    }
}
