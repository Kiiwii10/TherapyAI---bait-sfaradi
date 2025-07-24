package com.example.therapyai.ui.viewmodels;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.repository.CardRepository; // Import Repository
import java.util.ArrayList;
import java.util.List;

public class CardViewModel extends AndroidViewModel {

    private static final String TAG = "CardViewModel";

    private final CardRepository cardRepository;

    private final MutableLiveData<List<CardItem>> _cardListLiveData = new MutableLiveData<>();
    public final LiveData<List<CardItem>> cardListLiveData = _cardListLiveData;

    private final MutableLiveData<String> _errorLiveData = new MutableLiveData<>();
    public final LiveData<String> errorLiveData = _errorLiveData;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;


    private String currentLoadedCategory = "All";

    public CardViewModel(@NonNull Application application) {
        super(application);
        cardRepository = CardRepository.getInstance(application);
        Log.d(TAG, "ViewModel initialized with Repository.");
    }

    public LiveData<List<CardItem>> getCardList() {
        return cardListLiveData;
    }

    public void loadCards(@NonNull String category) {
        currentLoadedCategory = category;
        Log.d(TAG, "ViewModel: Requesting card load for category: " + currentLoadedCategory);
        _isLoading.postValue(true);
        cardRepository.loadCardsByCategory(currentLoadedCategory, new CardRepository.LoadCardsCallback() {
            @Override
            public void onCardsLoaded(List<CardItem> cards) {
                Log.d(TAG, "ViewModel: Received cards from repo. Count: " + cards.size());
                _cardListLiveData.postValue(cards);
                _isLoading.postValue(false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error loading cards from repo", e);
                _errorLiveData.postValue("Error loading cards: " + e.getMessage());
                _isLoading.postValue(false);
                _cardListLiveData.postValue(new ArrayList<>());
            }
        });
    }

    public void saveCard(CardItem card) {
        Log.d(TAG, "ViewModel: Requesting save card Title: " + card.getTitle());
        _isLoading.postValue(true);
        cardRepository.saveCard(card, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Card saved via repo. Reloading category: " + currentLoadedCategory);
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error saving card via repo", e);
                _errorLiveData.postValue("Error saving card: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    public void deleteCard(CardItem card) {
        Log.d(TAG, "ViewModel: Requesting delete card ID: " + card.getId());
        _isLoading.postValue(true);
        cardRepository.deleteCard(card, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Card deleted via repo. Reloading category: " + currentLoadedCategory);
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error deleting card via repo", e);
                _errorLiveData.postValue("Error deleting card: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    public void deleteSelectedCards(List<CardItem> cardsToDelete) {
        Log.d(TAG, "ViewModel: Requesting delete " + cardsToDelete.size() + " selected cards.");
        _isLoading.postValue(true);
        cardRepository.deleteSelectedCards(cardsToDelete, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Selected cards deleted via repo. Reloading category: " + currentLoadedCategory);
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error deleting selected cards via repo", e);
                _errorLiveData.postValue("Error deleting cards: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    public void updateCardOrder(List<CardItem> orderedCards) {
        Log.d(TAG, "ViewModel: Requesting update order for " + orderedCards.size() + " cards.");
        _isLoading.postValue(true);
        cardRepository.updateCardOrder(orderedCards, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Card order updated via repo.");
                _isLoading.postValue(false);
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error updating card order via repo", e);
                _errorLiveData.postValue("Error updating order: " + e.getMessage());
                _isLoading.postValue(false);
                loadCardsInternal(currentLoadedCategory);
            }
        });
    }

    public void removeCategoryFromCards(String categoryName, List<CardItem> cardsToUpdate) {
        Log.d(TAG, "ViewModel: Requesting remove category '" + categoryName + "'");
        _isLoading.postValue(true);
        cardRepository.removeCategoryFromCards(categoryName, cardsToUpdate, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: removeCategoryFromCards success via repo. Reloading.");
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: removeCategoryFromCards error via repo", e);
                _errorLiveData.postValue("Error removing category: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    public void renameCategoryInCards(String oldName, String newName, List<CardItem> cardsToUpdate) {
        Log.d(TAG, "ViewModel: Requesting rename category '" + oldName + "' to '" + newName + "'");
        _isLoading.postValue(true); // May take time
        cardRepository.renameCategoryInCards(oldName, newName, cardsToUpdate, new CardRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: renameCategoryInCards success via repo. Reloading.");
                loadCardsInternal(currentLoadedCategory);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: renameCategoryInCards error via repo", e);
                _errorLiveData.postValue("Error renaming category: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }


    // --- Private Helper ---
    private void loadCardsInternal(@NonNull String category) {
        cardRepository.loadCardsByCategory(category, new CardRepository.LoadCardsCallback() {
            @Override
            public void onCardsLoaded(List<CardItem> cards) {
                _cardListLiveData.postValue(cards);
                _isLoading.postValue(false);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Internal reload error", e);
                _errorLiveData.postValue("Error reloading cards: " + e.getMessage());
                _isLoading.postValue(false);
                _cardListLiveData.postValue(new ArrayList<>());
            }
        });
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel cleared.");
    }
}