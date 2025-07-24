package com.example.therapyai.ui.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.therapyai.data.local.models.PendingInboxItem;
import com.example.therapyai.data.repository.ProcessedDataRepository;

import java.util.List;

public class InboxViewModel extends ViewModel {
    private final ProcessedDataRepository repository;
    private final LiveData<List<PendingInboxItem>> pendingItems;
    private final LiveData<Integer> pendingCount;
    private final LiveData<Boolean> isLoading;
    private final LiveData<String> errorState;


    public InboxViewModel() {
        repository = ProcessedDataRepository.getInstance();
        pendingItems = repository.getPendingInboxItems();
        pendingCount = repository.getPendingCount();
        isLoading = repository.isLoading();
        errorState = repository.getErrorState();
    }

    public LiveData<List<PendingInboxItem>> getPendingItems() {
        return pendingItems;
    }

    public LiveData<Integer> getPendingCount() {
        return pendingCount;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorState() {
        return errorState;
    }

    public void refreshData() {
        repository.refreshPendingData();
    }
}
