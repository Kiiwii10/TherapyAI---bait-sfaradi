package com.example.therapyai.ui.sessions.session;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.therapyai.R;
import com.example.therapyai.data.local.models.NoteCard;
import com.example.therapyai.ui.adapters.NoteCardAdapter; // Ensure this adapter exists and works
import com.example.therapyai.ui.viewmodels.SessionViewModel; // <<< Use SessionViewModel
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ReviewSubmitFragment extends Fragment {

    private static final String TAG = "ReviewSubmitFragment";
    private SessionViewModel viewModel;
    private RecyclerView cardRecyclerView;    private NoteCardAdapter noteCardAdapter;
    private MaterialButton btnDeleteSession;
    private MaterialButton btnSendSession;
    
    // Progress UI elements
    private LinearLayout uploadProgressLayout;
    private LinearProgressIndicator uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadProgressPercent;

    public static ReviewSubmitFragment newInstance() {
        return new ReviewSubmitFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        viewModel = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_review_submit, container, false);
        initializeViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        setupRecyclerView();
        setupButtonListeners();

        Boolean currentLoadingState = viewModel.isLoading.getValue();
        Log.d(TAG, "Initial isLoading state check from ViewModel: " + currentLoadingState);
        boolean buttonsEnabledInitially = (currentLoadingState == null || !currentLoadingState);
        if (btnDeleteSession != null) {
            btnDeleteSession.setEnabled(buttonsEnabledInitially);
            Log.d(TAG,"Setting initial delete button enabled: " + buttonsEnabledInitially);
        }
        if (btnSendSession != null) {
            btnSendSession.setEnabled(buttonsEnabledInitially);
            Log.d(TAG,"Setting initial send button enabled: " + buttonsEnabledInitially);
        }

        setupViewModelObservers();
    }    private void initializeViews(View view) {
        cardRecyclerView = view.findViewById(R.id.cardRecyclerView_review);
        btnDeleteSession = view.findViewById(R.id.btnDeleteSession_review);
        btnSendSession = view.findViewById(R.id.btnSendSession_review);
        
        // Initialize progress UI elements
        uploadProgressLayout = view.findViewById(R.id.uploadProgressLayout_review);
        uploadProgressBar = view.findViewById(R.id.uploadProgressBar_review);
        uploadProgressText = view.findViewById(R.id.uploadProgressText_review);
        uploadProgressPercent = view.findViewById(R.id.uploadProgressPercent_review);
    }

    private void setupRecyclerView() {
        noteCardAdapter = new NoteCardAdapter(new ArrayList<>(), this::showEditNoteDialog);
        cardRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        cardRecyclerView.setAdapter(noteCardAdapter);
    }

    private void setupButtonListeners() {
        btnDeleteSession.setOnClickListener(v -> {
            Log.d(TAG, "Delete button clicked. Requesting delete from ViewModel.");
            viewModel.requestDelete();
        });
        btnSendSession.setOnClickListener(v -> {
            Log.d(TAG, "Submit button clicked. Requesting submit from ViewModel.");
            viewModel.requestSubmit();
        });
    }

    private void setupViewModelObservers() {
        // Observe combined notes from ViewModel
        viewModel.combinedNotes.observe(getViewLifecycleOwner(), notes -> {
            Log.d(TAG, "Observed combined notes update. Count: " + (notes != null ? notes.size() : 0));
            if (notes != null && noteCardAdapter != null) {
                noteCardAdapter.updateNotes(new ArrayList<>(notes));
            } else if (noteCardAdapter != null) {
                noteCardAdapter.updateNotes(new ArrayList<>());
            }
        });        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            Log.d(TAG,"Observed isLoading update: " + isLoading);
            if (btnDeleteSession != null) btnDeleteSession.setEnabled(!isLoading);
            if (btnSendSession != null) btnSendSession.setEnabled(!isLoading);
            
            // Show/hide progress layout based on loading state
            if (uploadProgressLayout != null) {
                uploadProgressLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });

        // Observe upload progress
        viewModel.uploadProgress.observe(getViewLifecycleOwner(), progress -> {
            Log.d(TAG, "Observed upload progress update: " + progress + "%");
            updateProgressUI(progress);
        });

        // Observe upload status
        viewModel.uploadStatus.observe(getViewLifecycleOwner(), status -> {
            Log.d(TAG, "Observed upload status update: " + status);
            updateStatusUI(status);
        });
    }

    private void showEditNoteDialog(NoteCard noteCardToEdit) {
        if (getContext() == null || noteCardAdapter == null) {
            Log.e(TAG,"Cannot show edit dialog, context or adapter is null.");
            return;
        }

        List<NoteCard> currentNotes = noteCardAdapter.getCurrentNotes();
        int currentAdapterPosition = -1;
        for(int i=0; i<currentNotes.size(); i++){
            if(currentNotes.get(i).getIndex() == noteCardToEdit.getIndex() &&
                    Objects.equals(currentNotes.get(i).getTitle(), noteCardToEdit.getTitle())) {
                currentAdapterPosition = i;
                break;
            }
        }

        if (currentAdapterPosition == -1) {
            Log.e(TAG, "Could not find note to edit in adapter's list: " + noteCardToEdit.getTitle());
            Toast.makeText(getContext(), "Error finding note.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_note, null);
        EditText etNote = dialogView.findViewById(R.id.etNote);
        dialogView.findViewById(R.id.cbTimestamp).setVisibility(View.GONE);
        etNote.setText(noteCardToEdit.getContent());

        String dialogTitle = getString(R.string.dialog_title_edit_note) + " (" + noteCardToEdit.getTitle() + ")";
        boolean isSummary = "Summary".equals(noteCardToEdit.getTitle());

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext())
                .setTitle(dialogTitle)
                .setView(dialogView)
                .setPositiveButton(R.string.button_save, null)
                .setNegativeButton(R.string.button_cancel, null);

        if (!isSummary) {
            dialogBuilder.setNeutralButton(getString(R.string.button_delete), (dialogInterface, i) -> {
                Log.d(TAG, "Delete (Neutral) button clicked for note: " + noteCardToEdit.getTitle());
                viewModel.deleteOtherNote(noteCardToEdit);
            });
        }

        AlertDialog dialog = dialogBuilder.create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String updatedNoteText = etNote.getText().toString().trim();

                if (isSummary && updatedNoteText.isEmpty()) {
                    etNote.setError(getString(R.string.error_summary_cannot_be_empty));
                    Toast.makeText(getContext(), R.string.error_summary_cannot_be_empty, Toast.LENGTH_SHORT).show();
                    return; // DO NOT DISMISS
                }

                if (!updatedNoteText.equals(noteCardToEdit.getContent())) {
                    Log.d(TAG, "Saving updated content for note: " + noteCardToEdit.getTitle());
                    if (isSummary) {
                        viewModel.updateSummary(updatedNoteText);
                    } else {
                        // Create a new NoteCard with updated content instead of modifying the original
                        NoteCard updatedNote = new NoteCard(noteCardToEdit.getTitle(), noteCardToEdit.getTimestamp(), updatedNoteText, noteCardToEdit.getIndex());
                        viewModel.saveOtherNote(updatedNote);
                    }
                } else {
                    Log.d(TAG, "Content unchanged for note: " + noteCardToEdit.getTitle());
                }
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void updateProgressUI(int progress) {
        if (uploadProgressBar != null && uploadProgressPercent != null) {
            uploadProgressBar.setProgress(progress);
            uploadProgressPercent.setText(progress + "%");
        }
    }

    private void updateStatusUI(String status) {
        if (uploadProgressText != null && status != null && !status.isEmpty()) {
            uploadProgressText.setText(status);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
        cardRecyclerView = null;
        noteCardAdapter = null;
        btnDeleteSession = null;
        btnSendSession = null;
//        progressBar = null;
    }
}