
package com.example.therapyai.ui.viewmodels;
import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.therapyai.data.local.models.FinalSessionDetail;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.util.Event;

public class DataViewModel extends AndroidViewModel {

    private final SearchRepository searchRepository;

    private final MutableLiveData<FinalSessionDetail> _sessionDetail = new MutableLiveData<>();
    public final LiveData<FinalSessionDetail> sessionDetail = _sessionDetail;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public final LiveData<String> getError() { return _error; }

    private final MutableLiveData<Event<Boolean>> _navigateToTranscriptEvent = new MutableLiveData<>();
    public LiveData<Event<Boolean>> getNavigateToTranscriptEvent() { return _navigateToTranscriptEvent; }


    private final MutableLiveData<Event<Integer>> _highlightRequest = new MutableLiveData<>();
    public LiveData<Event<Integer>> getHighlightRequest() { return _highlightRequest; }


    public DataViewModel(@NonNull Application application) {
        super(application);
        searchRepository = SearchRepository.getInstance();
    }

    public void fetchSessionDetails(String sessionId) {
        if (Boolean.TRUE.equals(_isLoading.getValue())) {
            return;
        }
        _isLoading.setValue(true);
        _error.setValue(null);

        searchRepository.getSessionDetails(sessionId, new SearchRepository.SessionDetailCallback() {
            @Override
            public void onSessionDetailFound(FinalSessionDetail detail) {
                _sessionDetail.postValue(detail);
                _isLoading.postValue(false);
            }

            @Override
            public void onError(String errorMsg) {
                _error.postValue(errorMsg);
                _isLoading.postValue(false);
            }
        });
    }

    public void requestNavigateToTranscript() {
        _navigateToTranscriptEvent.setValue(new Event<>(true));
    }

    public void requestNavigationAndHighlight(int index) {
        _highlightRequest.setValue(new Event<>(index)); // Request highlight
        _navigateToTranscriptEvent.setValue(new Event<>(true)); // Request navigation
    }

    public void requestHighlight(int index) {
        _highlightRequest.setValue(new Event<>(index));
    }

    public LiveData<FinalSessionDetail> getSessionDetail() {
        return sessionDetail;
    }
}
