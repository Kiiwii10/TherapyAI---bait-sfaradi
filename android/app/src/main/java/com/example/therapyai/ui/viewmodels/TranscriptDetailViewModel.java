package com.example.therapyai.ui.viewmodels;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Transformations;


import com.example.therapyai.data.local.models.TranscriptDetail;
import com.example.therapyai.data.local.models.TranscriptItem;
import com.example.therapyai.data.repository.ProcessedDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TranscriptDetailViewModel extends ViewModel {

    private final ProcessedDataRepository repository;
    private String currentDataId = null;

    private final MutableLiveData<String> dataIdToLoad = new MutableLiveData<>();

    private final LiveData<TranscriptDetail> transcriptDetail;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorState;

    private final MutableLiveData<List<TranscriptItem>> editableTranscriptItems = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasUnsavedChanges = new MutableLiveData<>(false);

    public TranscriptDetailViewModel() {
        repository = ProcessedDataRepository.getInstance();

        isLoading = repository.isLoading();
        errorState = repository.getErrorState();

        transcriptDetail = Transformations.switchMap(dataIdToLoad, id -> {
            if (id == null || id.isEmpty()) {
                return new MutableLiveData<>(null);
            }
            currentDataId = id;
            repository.fetchTranscriptDetail(id);
            return repository.getCurrentTranscriptDetail();
        });

        transcriptDetail.observeForever(detail -> {
            if (detail != null && detail.getTranscriptItems() != null) {
                editableTranscriptItems.postValue(new ArrayList<>(detail.getTranscriptItems()));
                hasUnsavedChanges.postValue(false);
            } else {
                editableTranscriptItems.postValue(new ArrayList<>());
            }
        });
    }

    public LiveData<TranscriptDetail> getTranscriptDetail() {
        return transcriptDetail;
    }

    public LiveData<List<TranscriptItem>> getEditableTranscriptItems() {
        return editableTranscriptItems;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorState() {
        return errorState;
    }

    public LiveData<Boolean> hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    // --- Actions ---
    public void loadData(String dataId) {
        if (!Objects.equals(dataId, dataIdToLoad.getValue())) {
            dataIdToLoad.setValue(dataId);
        }
    }

    public void notifyTranscriptChanged(List<TranscriptItem> updatedList) {
        TranscriptDetail originalDetail = transcriptDetail.getValue();
        boolean changed = false;
        if (originalDetail != null && originalDetail.getTranscriptItems() != null) {
            if(updatedList.size() != originalDetail.getTranscriptItems().size()) {
                changed = true;
            } else {
                for(int i=0; i<updatedList.size(); i++) {
                    if(updatedList.get(i).hasChanged()){
                        changed = true;
                        break;
                    }
                }
            }
        } else if (!updatedList.isEmpty()) {
            changed = true;
        }


        hasUnsavedChanges.setValue(changed);
    }


    public void swapAllSpeakers() {
        List<TranscriptItem> currentItems = editableTranscriptItems.getValue();
        if (currentItems != null && !currentItems.isEmpty()) {
            boolean speakersWereSwapped = false;
            Log.d("ViewModelSwap", "Before swap:");
            for (TranscriptItem item : currentItems) {
                Log.d("ViewModelSwap", " - Speaker: " + item.getSpeaker() + ", Text: " + item.getText().substring(0, Math.min(10, item.getText().length())));
                String currentSpeaker = item.getSpeaker();
                String newSpeaker = "Patient".equalsIgnoreCase(currentSpeaker) ? "Therapist" : "Patient";
                if (!Objects.equals(currentSpeaker, newSpeaker)) {
                    item.setSpeaker(newSpeaker);
                    speakersWereSwapped = true;
                }
            }

            if (speakersWereSwapped) {
                Log.d("ViewModelSwap", "Speakers were swapped. Posting updated list.");
                editableTranscriptItems.postValue(currentItems);
                hasUnsavedChanges.postValue(true);
            } else {
                Log.d("ViewModelSwap", "No speakers needed swapping.");
            }
        } else {
            Log.d("ViewModelSwap", "Current items list is null or empty.");
        }
    }


    public List<TranscriptItem> getFinalTranscriptList() {
        return editableTranscriptItems.getValue();
    }

    public void submitData(ProcessedDataRepository.OperationCallback callback) {
        List<TranscriptItem> itemsToSubmit = getFinalTranscriptList();
        if (currentDataId != null && itemsToSubmit != null) {
            repository.submitFinalTranscript(currentDataId, itemsToSubmit, new ProcessedDataRepository.OperationCallback() {
                @Override
                public void onSuccess() {
                    hasUnsavedChanges.postValue(false);
                    callback.onSuccess();
                }

                @Override
                public void onError(String errorMessage) {
                    callback.onError(errorMessage);
                }
            });
        } else {
            callback.onError("Missing data ID or transcript items for submission.");
        }
    }

}
