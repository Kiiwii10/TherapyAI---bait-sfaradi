package com.example.therapyai.data.repository;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.models.PendingInboxItem;
import com.example.therapyai.data.local.models.TranscriptDetail;
import com.example.therapyai.data.local.models.TranscriptItem;
import com.example.therapyai.data.remote.TherapyApiImpl;
import com.example.therapyai.data.remote.models.ProcessedDataEntryResponse;
import com.example.therapyai.data.remote.models.TranscriptSentenceResponse;
import com.example.therapyai.data.remote.models.TranscriptDetailResponse;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessedDataRepository {

    private static final String TAG = "ProcessedDataRepository";
    private static ProcessedDataRepository instance;
    private final TherapyApiImpl apiImpl;
    private final Gson gson = new Gson();

    private final MutableLiveData<List<PendingInboxItem>> pendingDataLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> pendingCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<TranscriptDetail> currentTranscriptDetail = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorState = new MutableLiveData<>();


    private ProcessedDataRepository(TherapyApiImpl apiImpl) {
        this.apiImpl = apiImpl;
        pendingDataLiveData.observeForever(list -> pendingCountLiveData.postValue(list != null ? list.size() : 0));
    }

    public static synchronized void init(boolean useMockData) {
        if (instance == null) {
            instance = new ProcessedDataRepository(TherapyApiImpl.getInstance(useMockData));
        }
    }

    public static synchronized ProcessedDataRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ProcessedDataRepository not initialized!");
        }
        return instance;
    }

    public LiveData<List<PendingInboxItem>> getPendingInboxItems() {
        if (pendingDataLiveData.getValue() == null || pendingDataLiveData.getValue().isEmpty()) {
            refreshPendingData();
        }
        return pendingDataLiveData;
    }

    public LiveData<Integer> getPendingCount() {
        return pendingCountLiveData;
    }

    public LiveData<TranscriptDetail> getCurrentTranscriptDetail() {
        return currentTranscriptDetail;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorState() {
        return errorState;
    }



    public void refreshPendingData() {
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null) {
            Log.d(TAG, "No token available, user might be logged out");
            pendingDataLiveData.postValue(new ArrayList<>());
            errorState.postValue("User not logged in.");
            return;
        }
        isLoading.postValue(true);
        errorState.postValue(null);

        apiImpl.refreshPendingData("Bearer " + token, new TherapyApiImpl.ApiCallback<List<ProcessedDataEntryResponse>>() {
            @Override
            public void onSuccess(List<ProcessedDataEntryResponse> result) {
                isLoading.postValue(false);
                if (result != null) {
                    List<PendingInboxItem> localItems = result.stream()
                            .map(ProcessedDataRepository::toPendingInboxItem)
                            .collect(Collectors.toList());
                    pendingDataLiveData.postValue(localItems);
                    Log.d(TAG, "Pending data updated: " + localItems.size() + " items");
                } else {
                    pendingDataLiveData.postValue(new ArrayList<>());
                }
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                Log.e(TAG, "Failed to get pending data: " + error);
                pendingDataLiveData.postValue(new ArrayList<>());
                errorState.postValue("Failed to load inbox: " + error);
            }
        });
    }

    public void fetchTranscriptDetail(String dataId) {
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null) {
            Log.e(TAG, "No token available for fetchTranscriptDetail");
            errorState.postValue("Authentication error. Please log in again.");
            currentTranscriptDetail.postValue(null);
            return;
        }
        isLoading.postValue(true);
        errorState.postValue(null);

        apiImpl.getSessionTranscriptDetail(token, dataId, new TherapyApiImpl.ApiCallback<TranscriptDetailResponse>() {
            @Override
            public void onSuccess(TranscriptDetailResponse result) {
                isLoading.postValue(false);
                if (result != null) {
                    TranscriptDetail detail = toTranscriptDetail(result);
                    currentTranscriptDetail.postValue(detail);
                    Log.d(TAG, "Transcript detail loaded successfully for ID: " + dataId);
                } else {
                    Log.e(TAG, "Received null transcript detail response for ID: " + dataId);
                    currentTranscriptDetail.postValue(null);
                    errorState.postValue("Failed to load transcript details (empty response).");
                }
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                Log.e(TAG, "Failed to get transcript detail for ID " + dataId + ": " + error);
                currentTranscriptDetail.postValue(null); // Clear data on error
                errorState.postValue("Failed to load transcript: " + error);
            }
        });
    }

    public void submitFinalTranscript(String dataId, List<TranscriptItem> editedItems, final OperationCallback callback) {
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null) {
            callback.onError("No token available");
            return;
        }
        isLoading.postValue(true);
        errorState.postValue(null);

        List<TranscriptSentenceResponse> submissionList = editedItems.stream()
                .map(item -> {
                    TranscriptSentenceResponse resp = new TranscriptSentenceResponse();
                    resp.setSpeaker(item.getSpeaker());
                    resp.setText(item.getText());
                    resp.setTimestamp(item.getTimestamp());
                    return resp;
                })
                .collect(Collectors.toList());

        apiImpl.submitFinalTranscript(token, dataId, submissionList, new TherapyApiImpl.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                isLoading.postValue(false);
                refreshPendingData();
                callback.onSuccess();
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
                Log.e(TAG,"Error submitting final transcript: " + error);
                callback.onError("Failed to submit final transcript: " + error);
            }
        });
    }


    private static PendingInboxItem toPendingInboxItem(ProcessedDataEntryResponse response) {
        if (response == null) return null;
        return new PendingInboxItem(
                response.getId(),
                response.getPatientName(),
                response.getSessionDate(),
                response.getSummaryPreview()
        );
    }

    private static TranscriptItem toTranscriptItem(TranscriptSentenceResponse response) {
        if (response == null) return null;
        return new TranscriptItem(
                response.getSpeaker(),
                response.getText(),
                response.getTimestamp()
        );
    }

    private static TranscriptDetail toTranscriptDetail(TranscriptDetailResponse response) {
        if (response == null) return null;

        List<TranscriptItem> items = new ArrayList<>();
        if (response.getTranscript() != null) {
            items = response.getTranscript().stream()
                    .map(ProcessedDataRepository::toTranscriptItem)
                    .collect(Collectors.toList());
        }

        return new TranscriptDetail(
                response.getPatientName(),
                response.getSessionDate(),
                response.getSummary(),
                items
        );
    }


    public interface OperationCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

}
