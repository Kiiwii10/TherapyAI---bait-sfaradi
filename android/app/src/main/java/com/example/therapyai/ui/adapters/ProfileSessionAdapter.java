package com.example.therapyai.ui.adapters;

import android.content.Context;
import android.os.Handler; // Import Handler
import android.os.Looper;  // Import Looper
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.therapyai.R;
import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.SessionSummary;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileSessionAdapter extends RecyclerView.Adapter<ProfileSessionAdapter.ViewHolder> {

    private final Context context;
    private List<SessionSummary> sessionScores;
    private final OnSessionClickListener listener;

    private int highlightedPosition = RecyclerView.NO_POSITION;
    private final Handler highlightHandler = new Handler(Looper.getMainLooper());
    private Runnable clearHighlightRunnable = null;
    private static final long HIGHLIGHT_DURATION_MS = 750;
    private static final String TAG = "ProfileSessionAdapter";


    // Date formatting
    private final SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public interface OnSessionClickListener {
        void onSessionClick(SessionSummary sessionSummary, int position);
    }

    public ProfileSessionAdapter(Context context, OnSessionClickListener listener) {
        this.context = context;
        this.sessionScores = new ArrayList<>();
        this.listener = listener;
    }

    public void setData(List<SessionSummary> scores) {
        this.sessionScores = (scores != null) ? scores : new ArrayList<>();

        int oldHighlight = highlightedPosition;
        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
            clearHighlightRunnable = null;
        }
        highlightedPosition = RecyclerView.NO_POSITION;

        notifyDataSetChanged(); // Refresh the whole list
    }

    public void setHighlightPosition(int position) {
        if (position < 0 || position >= getItemCount()) {
            position = RecyclerView.NO_POSITION;
        }
        // No need to proceed if highlight isn't changing
        if (highlightedPosition == position) {

            return;
        }

        // Cancel any pending highlight removal
        if (clearHighlightRunnable != null) {
            highlightHandler.removeCallbacks(clearHighlightRunnable);
            clearHighlightRunnable = null;
            Log.v(TAG,"setHighlightPosition: Cleared pending highlight removal.");
        }

        int previousHighlight = highlightedPosition;
        highlightedPosition = position;
        Log.d(TAG, "setHighlightPosition: Changing highlight from " + previousHighlight + " to " + highlightedPosition);

        // Notify previous item to remove highlight (using payload)
        if (previousHighlight != RecyclerView.NO_POSITION) {
            if (previousHighlight < getItemCount()) {
                notifyItemChanged(previousHighlight, "CLEAR_HIGHLIGHT");
            }
        }
        // Notify new item to add highlight (using payload)
        if (highlightedPosition != RecyclerView.NO_POSITION) {
            if (highlightedPosition < getItemCount()) {
                notifyItemChanged(highlightedPosition, "SET_HIGHLIGHT");

                // Schedule removal of highlight
                clearHighlightRunnable = () -> {
                    Log.d(TAG, "Highlight timer expired for position: " + highlightedPosition);
                    int currentHighlight = highlightedPosition;
                    if (currentHighlight != RecyclerView.NO_POSITION) {
                        highlightedPosition = RecyclerView.NO_POSITION;
                        // Check again if position is valid before notifying
                        if (currentHighlight < getItemCount()) {
                            notifyItemChanged(currentHighlight, "CLEAR_HIGHLIGHT");
                            Log.d(TAG, "Highlight timer: Cleared highlight for position: " + currentHighlight);
                        }
                    }
                    clearHighlightRunnable = null; // Clear runnable reference
                };
                highlightHandler.postDelayed(clearHighlightRunnable, HIGHLIGHT_DURATION_MS);
                Log.v(TAG, "setHighlightPosition: Scheduled highlight removal for pos " + highlightedPosition);
            } else {
                Log.w(TAG, "setHighlightPosition: New highlight position " + highlightedPosition + " is out of bounds (" + getItemCount() + ")");
                highlightedPosition = RecyclerView.NO_POSITION; // Reset if invalid
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_search_session_summary, parent, false);
        return new ViewHolder(view);
    }

    // --- Full Bind ---
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SessionSummary score = sessionScores.get(position);
        boolean shouldHighlight = (position == highlightedPosition);
        holder.bind(score, listener, shouldHighlight); // Pass current state
    }

    // --- Partial Bind (for Payloads) ---
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            boolean highlightChanged = false;
            for (Object payload : payloads) {
                if ("SET_HIGHLIGHT".equals(payload)) {
                    holder.setHighlightState(true);
                    highlightChanged = true;
                    Log.v(TAG, "Payload: SET_HIGHLIGHT for pos " + position);
                    break;
                } else if ("CLEAR_HIGHLIGHT".equals(payload)) {
                    holder.setHighlightState(false);
                    highlightChanged = true;
                    Log.v(TAG, "Payload: CLEAR_HIGHLIGHT for pos " + position);
                    break;
                }
            }
            if (!highlightChanged) {
                onBindViewHolder(holder, position);
            }
        }
    }

    @Override
    public int getItemCount() {
        return sessionScores.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionDate;
        TextView tvSessionShortDesc;
        TextView tvSessionTitle;
        View itemView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView; // Store itemView
            tvSessionDate = itemView.findViewById(R.id.tvSessionDate);
            tvSessionShortDesc = itemView.findViewById(R.id.tvSessionDescription);
            tvSessionTitle = itemView.findViewById(R.id.tvSessionTitle);
        }

        public void bind(final SessionSummary sessionSummary, final OnSessionClickListener listener, boolean isHighlighted) {
            setHighlightState(isHighlighted);
            String formattedDate = sessionSummary.getDate();
            try {
                if (sessionSummary.getDate() != null) {
                    Date date = inputFormat.parse(sessionSummary.getDate());
                    if (date != null) {
                        formattedDate = outputFormat.format(date);
                    }
                }
            } catch (ParseException | NullPointerException e) {
                Log.w(TAG, "Could not parse date: " + sessionSummary.getDate(), e);
            }
            tvSessionTitle.setText(sessionSummary.getTitle() != null && !sessionSummary.getTitle().isEmpty() ? sessionSummary.getTitle() : "");
            tvSessionDate.setText(formattedDate);
            tvSessionShortDesc.setText(sessionSummary.getDescriptionPreview() != null ? sessionSummary.getDescriptionPreview() : "");

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        listener.onSessionClick(sessionScores.get(currentPosition), currentPosition);
                    }
                }
            });
        }

        void setHighlightState(boolean highlighted) {
            itemView.setActivated(highlighted);
            Log.v(TAG,"setHighlightState: Pos " + getBindingAdapterPosition() + " Activated: " + highlighted);
        }
    }
}