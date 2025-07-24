package com.example.therapyai.ui.viewmodels;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.NoteCard;
import com.example.therapyai.util.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SessionViewModel extends ViewModel {

    private static final String TAG = "SessionViewModel";

    // --- Existing LiveData ---
    private final MutableLiveData<CardItem> _selectedCard = new MutableLiveData<>();
    public LiveData<CardItem> selectedCard = _selectedCard;

    private final MutableLiveData<String> _patientData = new MutableLiveData<>();
    public LiveData<String> patientData = _patientData;

    // --- NEW: For encrypted Data Encryption Key (DEK) ---
    private final MutableLiveData<String> _encryptedDataEncryptionKey = new MutableLiveData<>();
    public LiveData<String> encryptedDataEncryptionKey = _encryptedDataEncryptionKey;
    // ---

    private final MutableLiveData<String> _encryptedAudioFilePath = new MutableLiveData<>();
    public LiveData<String> encryptedAudioFilePath = _encryptedAudioFilePath;

    private final MutableLiveData<List<String>> _encryptedAudioFilePaths = new MutableLiveData<>();

    public LiveData<List<String>> encryptedAudioFilePaths = _encryptedAudioFilePaths;


    private final MutableLiveData<String> _vrDataPath = new MutableLiveData<>();
    public LiveData<String> vrDataPath = _vrDataPath;

    private final MutableLiveData<String> _physioDataPath = new MutableLiveData<>();
    public LiveData<String> physioDataPath = _physioDataPath;

    private final MutableLiveData<NoteCard> _summaryNote = new MutableLiveData<>();
    public LiveData<NoteCard> summaryNote = _summaryNote;

    private final MutableLiveData<List<NoteCard>> _otherNotes = new MutableLiveData<>(new ArrayList<>());
    public LiveData<List<NoteCard>> otherNotes = _otherNotes;

    private final MediatorLiveData<List<NoteCard>> _combinedNotes = new MediatorLiveData<>();
    public LiveData<List<NoteCard>> combinedNotes = _combinedNotes;

    private final MutableLiveData<Boolean> _isSummaryReady = new MutableLiveData<>(false);
    public LiveData<Boolean> isSummaryReady = _isSummaryReady;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isLoading = _isLoading;

    // NEW: Upload progress tracking
    private final MutableLiveData<Integer> _uploadProgress = new MutableLiveData<>(0);
    public LiveData<Integer> uploadProgress = _uploadProgress;

    private final MutableLiveData<String> _uploadStatus = new MutableLiveData<>("");
    public LiveData<String> uploadStatus = _uploadStatus;

    private final MutableLiveData<Event<Unit>> _triggerSubmit = new MutableLiveData<>();
    public LiveData<Event<Unit>> triggerSubmit = _triggerSubmit;

    private final MutableLiveData<Event<Unit>> _triggerDelete = new MutableLiveData<>();
    public LiveData<Event<Unit>> triggerDelete = _triggerDelete;

    private final MutableLiveData<Event<NavigationCommand>> _navigationCommand = new MutableLiveData<>();
    public LiveData<Event<NavigationCommand>> navigationCommand = _navigationCommand;

    public enum AuthReason { START_RECORDING, SUBMIT_SESSION }

    private final MutableLiveData<Event<AuthReason>> _userAuthRequired = new MutableLiveData<>();
    public LiveData<Event<AuthReason>> userAuthRequired = _userAuthRequired;

    private final MutableLiveData<Event<String>> _errorMessage = new MutableLiveData<>();
    public LiveData<Event<String>> errorMessage = _errorMessage;

    public SessionViewModel() {
        _combinedNotes.addSource(_summaryNote, summary -> combineNotes());
        _combinedNotes.addSource(_otherNotes, others -> combineNotes());
    }

    private void combineNotes() {
        NoteCard summary = _summaryNote.getValue();
        List<NoteCard> others = _otherNotes.getValue();
        if (others == null) others = Collections.emptyList();

        List<NoteCard> combined = new ArrayList<>();
        if (summary != null) {
            combined.add(summary);
        }
        combined.addAll(others);
        _combinedNotes.setValue(combined);
        // Log.d(TAG, "Combined notes updated. Summary: " + (summary != null) + ", Others: " + others.size() + ", Total: " + combined.size());
    }

    public void setSelectedCard(CardItem card) {
        if (_selectedCard.getValue() == null || !_selectedCard.getValue().equals(card)) { // Avoid redundant sets
            _selectedCard.setValue(card);
            Log.d(TAG, "Selected Card set: " + (card != null ? card.getTitle() : "null"));
        }
    }

    public void setPatientData(String data) {
        if (!Objects.equals(_patientData.getValue(), data)) {
            _patientData.setValue(data);
            Log.d(TAG, "Patient data set.");
        }
    }

    public void setEncryptedAudioFilePath(String path) {
        if (!Objects.equals(_encryptedAudioFilePath.getValue(), path)) {
            _encryptedAudioFilePath.setValue(path);
            Log.d(TAG, "Encrypted audio path set: " + path);
        }
    }

    public void setEncryptedAudioFilePaths(List<String> paths) {
        if (!Objects.equals(_encryptedAudioFilePaths.getValue(), paths)) {
            _encryptedAudioFilePaths.setValue(paths);
            Log.d(TAG, "Encrypted audio paths set: " + paths);
        }
    }

    public void setEncryptedDataEncryptionKey(String encryptedDekBase64) {
        if (!Objects.equals(_encryptedDataEncryptionKey.getValue(), encryptedDekBase64)) {
            _encryptedDataEncryptionKey.setValue(encryptedDekBase64);
            // Avoid logging the key itself for security, even if it's encrypted
            Log.d(TAG, "Encrypted Data Encryption Key (DEK) has been set in ViewModel.");
        }
    }

    public void clearSensitiveSessionData() {
        _encryptedAudioFilePath.setValue(null); // Use setValue for synchronous clearing if on main thread
        _encryptedDataEncryptionKey.setValue(null);
        _encryptedAudioFilePaths.postValue(null);
        Log.d(TAG, "Cleared sensitive session data (audio path, DEK) from ViewModel.");
    }
    // ---

    public void setVrDataPath(String path) {
        if (!Objects.equals(_vrDataPath.getValue(), path)) {
            _vrDataPath.setValue(path);
            Log.d(TAG, "VR data path set: " + path);
        }
    }

    public void setPhysioDataPath(String path) {
        if (!Objects.equals(_physioDataPath.getValue(), path)) {
            _physioDataPath.setValue(path);
            Log.d(TAG, "Physio data path set: " + path);
        }
    }

    public void updateSummary(String content) {
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "Attempted to update summary with empty content.");
            NoteCard currentSummary = _summaryNote.getValue();
            if (currentSummary != null && !currentSummary.getContent().isEmpty()) {
                currentSummary.setContent("");
                _summaryNote.setValue(currentSummary);
            } else if (currentSummary == null) {
                _summaryNote.setValue(new NoteCard("Summary", "", "", -1));
            }
            _isSummaryReady.setValue(false);
            return;
        }

        NoteCard currentSummary = _summaryNote.getValue();
        String trimmedContent = content.trim();
        if (currentSummary != null) {
            if (!trimmedContent.equals(currentSummary.getContent())) {
                currentSummary.setContent(trimmedContent);
                _summaryNote.setValue(currentSummary);
                Log.d(TAG, "Summary content updated.");
            } else {
                Log.d(TAG,"Summary content unchanged.");
            }
        } else {
            Log.d(TAG, "Creating new Summary note.");
            _summaryNote.setValue(new NoteCard("Summary", "", trimmedContent, -1));
        }
        _isSummaryReady.setValue(true);
        Log.d(TAG, "Summary updated and marked as ready.");
    }

    public void saveOtherNote(NoteCard noteToSave) {
        if (noteToSave == null || "Summary".equals(noteToSave.getTitle())) return;

        List<NoteCard> currentOthers = new ArrayList<>(_otherNotes.getValue() != null ? _otherNotes.getValue() : Collections.emptyList());
        int existingIndex = findNoteIndex(currentOthers, noteToSave);

        if (existingIndex != -1) {
            // Only update if content actually changed
            if (!Objects.equals(currentOthers.get(existingIndex).getContent(), noteToSave.getContent())) {
                currentOthers.set(existingIndex, noteToSave);
                Log.d(TAG,"Updated other note: " + noteToSave.getTitle());
            } else {
                Log.d(TAG,"Skipped updating other note (content same): " + noteToSave.getTitle());
                return; // No change, no need to setValue
            }
        } else {
            if (noteToSave.getIndex() < 0) {
                noteToSave.setIndex(getNextGenericNoteIndex(currentOthers));
            }
            currentOthers.add(noteToSave);
            Log.d(TAG,"Added other note: " + noteToSave.getTitle());
        }
        _otherNotes.setValue(currentOthers);
    }

    public void deleteOtherNote(NoteCard noteToDelete) {
        if (noteToDelete == null || "Summary".equals(noteToDelete.getTitle())) return;
        List<NoteCard> currentOthers = new ArrayList<>(_otherNotes.getValue() != null ? _otherNotes.getValue() : Collections.emptyList());
        int indexToRemove = findNoteIndex(currentOthers, noteToDelete);

        if (indexToRemove != -1) {
            currentOthers.remove(indexToRemove);
            _otherNotes.setValue(currentOthers);
            Log.d(TAG, "Deleted note: " + noteToDelete.getTitle());
        } else {
            Log.w(TAG, "Note not found for deletion: " + noteToDelete.getTitle());
        }
    }

    private int findNoteIndex(List<NoteCard> notes, NoteCard target) {
        if (target == null || notes == null) return -1;
        for (int i = 0; i < notes.size(); i++) {
            NoteCard current = notes.get(i);
            if (current.getIndex() == target.getIndex() && Objects.equals(current.getTitle(), target.getTitle())) {
                return i;
            }
        }
        return -1;
    }

    private int getNextGenericNoteIndex(List<NoteCard> notes) {
        if (notes == null || notes.isEmpty()) return 0;
        int maxIndex = -1;
        for (NoteCard note : notes) {
            if (!"Summary".equals(note.getTitle())) {
                maxIndex = Math.max(maxIndex, note.getIndex());
            }
        }
        return maxIndex + 1;
    }

    public CardItem getSelectedCardValue() {
        return _selectedCard.getValue();
    }

    public void requestSubmit() {
        NoteCard summary = _summaryNote.getValue();
        if (Boolean.FALSE.equals(_isSummaryReady.getValue()) || summary == null || summary.getContent() == null || summary.getContent().trim().isEmpty()) {
            Log.e(TAG, "Submit requested but summary not ready or empty.");
            _errorMessage.setValue(new Event<>("Summary cannot be empty. Please add a summary before submitting."));
            setLoading(false);
            return;
        }
        Log.d(TAG, "Submit session requested.");
        _isLoading.setValue(true);
        _triggerSubmit.setValue(new Event<>(Unit.INSTANCE));
    }

    public void requestDelete() {
        Log.d(TAG, "Delete session requested.");
        _isLoading.setValue(true);
        _triggerDelete.setValue(new Event<>(Unit.INSTANCE));
    }

    public void setLoading(boolean loading) {
        if (!Objects.equals(_isLoading.getValue(), loading)) {
            _isLoading.setValue(loading);
            Log.d(TAG, "isLoading set to: " + loading);
        }
    }

    public void navigateTo(int actionId) {
        _navigationCommand.setValue(new Event<>(new NavigationCommand.ToDirection(actionId)));
    }
    public void navigateBack() {
        _navigationCommand.setValue(new Event<>(NavigationCommand.Back.INSTANCE));
    }

    public void requestUserAuthentication(AuthReason reason) {
        Log.d(TAG, "User authentication requested for: " + reason);
        setLoading(false);
        _userAuthRequired.setValue(new Event<>(reason));
    }

    public void postErrorMessage(String message) {
        Log.e(TAG, "Error message posted: " + message);
        setLoading(false);
        _errorMessage.setValue(new Event<>(message));
    }

    public void updateUploadProgress(int progress) {
        if (progress < 0 || progress > 100) {
            Log.w(TAG, "Invalid upload progress value: " + progress);
            return;
        }
        _uploadProgress.setValue(progress);
        Log.d(TAG, "Upload progress updated: " + progress + "%");
    }

    public void updateUploadStatus(String status) {
        _uploadStatus.setValue(status);
        Log.d(TAG, "Upload status updated: " + status);
    }

    public void resetUploadProgress() {
        _uploadProgress.setValue(0);
        _uploadStatus.setValue("");
        Log.d(TAG, "Upload progress reset");
    }

    public static class Unit {
        public static final Unit INSTANCE = new Unit();
        private Unit() {}
    }

    public static abstract class NavigationCommand {
        public static final class ToDirection extends NavigationCommand {
            public final int actionId;
            public ToDirection(int actionId) { this.actionId = actionId; }
        }
        public static final class Back extends NavigationCommand {
            public static final Back INSTANCE = new Back();
            private Back() {}
        }
    }
}