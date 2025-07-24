package com.example.therapyai.data.repository; // Or your preferred repository package

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.therapyai.TherapyAIApp;
import com.example.therapyai.data.local.dao.CardDao;
import com.example.therapyai.data.local.db.AppDatabase;
import com.example.therapyai.data.local.models.CardItem;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CardRepository {

    private static final String TAG = "CardRepository";

    private final CardDao cardDao;
    private final ExecutorService databaseExecutor;

    // LiveData to hold the cards - could be managed here or passed from ViewModel
    // For simplicity now, let ViewModel manage the LiveData observation lifecycle
    // private final MutableLiveData<List<CardItem>> cardListLiveData = new MutableLiveData<>();

    // --- Singleton Pattern (Optional but common for repositories) ---
    private static volatile CardRepository INSTANCE;

    public static CardRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (CardRepository.class) {
                if (INSTANCE == null) {
                    AppDatabase database = TherapyAIApp.getInstance().getDb(); // Get DB instance
                    INSTANCE = new CardRepository(database.cardDao());
                }
            }
        }
        return INSTANCE;
    }
    // --- End Singleton Pattern ---


    // Private constructor for Singleton
    private CardRepository(CardDao cardDao) {
        this.cardDao = cardDao;
        // Using a single thread executor is generally safer for sequential DB operations
        this.databaseExecutor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "Repository initialized.");
    }

    // --- Public Data Access Methods ---

    /**
     * Loads cards for a specific category asynchronously.
     * Uses a callback to return the result to the caller (e.g., ViewModel).
     * @param category The category name ("All" for all).
     * @param callback Callback to handle the result or error.
     */
    public void loadCardsByCategory(@NonNull String category, @NonNull LoadCardsCallback callback) {
        databaseExecutor.execute(() -> {
            Log.d(TAG, "Repo: Executing DB query for category: " + category + " on thread: " + Thread.currentThread().getName());
            try {
                List<CardItem> cards;
                if ("All".equalsIgnoreCase(category)) {
                    cards = cardDao.getAllCards();
                } else {
                    cards = cardDao.getCardsByCategory(category);
                }
                Log.d(TAG, "Repo: DB query complete. Found " + (cards != null ? cards.size() : 0) + " cards.");
                // Use the callback to return the result
                callback.onCardsLoaded(cards != null ? cards : new ArrayList<>());
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error loading cards from database for category: " + category, e);
                callback.onError(e);
            }
        });
    }

    /**
     * Saves (inserts or updates) a card asynchronously.
     * @param card The card to save.
     * @param callback Optional callback for success/failure notification.
     */
    public void saveCard(CardItem card, @Nullable OperationCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                boolean isUpdate = card.getId() > 0;
                // Assign position if it's a new card
                if (!isUpdate && card.getPosition() < 0) {
                    int currentMaxPosition = cardDao.getMaxPosition();
                    card.setPosition(currentMaxPosition + 1);
                }

                if (isUpdate) {
                    Log.d(TAG, "Repo: Updating card ID: " + card.getId() + " on thread: " + Thread.currentThread().getName());
                    cardDao.updateCard(card);
                } else {
                    Log.d(TAG, "Repo: Inserting new card Title: " + card.getTitle() + " on thread: " + Thread.currentThread().getName());
                    cardDao.insertCard(card);
                }
                Log.d(TAG, "Repo: Card saved successfully.");
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error saving card (ID: " + card.getId() + ")", e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Deletes a single card asynchronously.
     * @param card The card to delete.
     * @param callback Optional callback for success/failure notification.
     */
    public void deleteCard(CardItem card, @Nullable OperationCallback callback) {
        if (card == null) {
            Log.w(TAG, "Repo: Attempted to delete a null card.");
            if (callback != null) callback.onError(new IllegalArgumentException("Card cannot be null"));
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                Log.d(TAG, "Repo: Deleting card ID: " + card.getId() + " on thread: " + Thread.currentThread().getName());
                cardDao.deleteCard(card);
                Log.d(TAG, "Repo: Card deleted successfully.");
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error deleting card (ID: " + card.getId() + ")", e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Deletes a list of cards asynchronously.
     * @param cardsToDelete List of cards to delete.
     * @param callback Optional callback for success/failure notification.
     */
    public void deleteSelectedCards(List<CardItem> cardsToDelete, @Nullable OperationCallback callback) {
        if (cardsToDelete == null || cardsToDelete.isEmpty()) {
            Log.w(TAG, "Repo: Attempted to delete empty/null card list.");
            if (callback != null) callback.onError(new IllegalArgumentException("Card list cannot be null or empty"));
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                Log.d(TAG, "Repo: Deleting " + cardsToDelete.size() + " cards on thread: " + Thread.currentThread().getName());
                cardDao.deleteCards(cardsToDelete);
                Log.d(TAG, "Repo: Selected cards deleted successfully.");
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error deleting selected cards", e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Updates the order (position field) of multiple cards asynchronously.
     * @param orderedCards List of cards in the new order.
     * @param callback Optional callback for success/failure notification.
     */
    public void updateCardOrder(List<CardItem> orderedCards, @Nullable OperationCallback callback) {
        if (orderedCards == null || orderedCards.isEmpty()) {
            Log.w(TAG, "Repo: Attempted to update order with empty/null list.");
            if (callback != null) callback.onError(new IllegalArgumentException("Ordered card list cannot be null or empty"));
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                Log.d(TAG, "Repo: Updating order for " + orderedCards.size() + " cards on thread: " + Thread.currentThread().getName());
                for (int i = 0; i < orderedCards.size(); i++) {
                    CardItem item = orderedCards.get(i);
                    item.setPosition(i);
                }
                cardDao.updateCards(orderedCards); // Bulk update
                Log.d(TAG, "Repo: Card order update successful.");
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error updating card order", e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Removes a specific category from a list of cards asynchronously.     * 
     * @param categoryName Category to remove.
     * @param cardsToUpdate List of cards potentially containing the category.
     * @param callback Optional callback for success/failure notification.
     */
    public void removeCategoryFromCards(String categoryName, List<CardItem> cardsToUpdate, @Nullable OperationCallback callback) {
        if (cardsToUpdate == null || categoryName == null) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid arguments for category removal"));
            return;
        }
        // If the list is empty, it's a valid case - just nothing to do
        if (cardsToUpdate.isEmpty()) {
            Log.d(TAG, "Repo: No cards to update for category removal: " + categoryName);
            if (callback != null) callback.onSuccess();
            return;
        }
        databaseExecutor.execute(() -> {
            Log.d(TAG, "Repo: Removing category '" + categoryName + "' from " + cardsToUpdate.size() + " cards.");
            List<CardItem> actuallyModified = new ArrayList<>();
            for (CardItem card : cardsToUpdate) {
                Set<String> cats = card.getCategories();
                if (cats != null && cats.remove(categoryName)) {
                    card.setCategories(cats);
                    actuallyModified.add(card);
                }
            }
            if (!actuallyModified.isEmpty()) {
                try {
                    cardDao.updateCards(actuallyModified);
                    Log.d(TAG, "Repo: Category '" + categoryName + "' removed from " + actuallyModified.size() + " cards.");
                    if (callback != null) callback.onSuccess();
                } catch (Exception e) {
                    Log.e(TAG, "Repo: Error updating cards after removing category '" + categoryName + "'", e);
                    if (callback != null) callback.onError(e);
                }
            } else {
                Log.d(TAG, "Repo: No cards needed updating for category removal: " + categoryName);
                if (callback != null) callback.onSuccess(); // Still success if nothing needed changing
            }
        });
    }

    /**
     * Renames a category within the category sets of affected cards asynchronously.
     * @param oldName The original category name.
     * @param newName The new category name.
     * @param cardsToUpdate List of cards potentially containing the old category.
     * @param callback Optional callback for success/failure notification.
     */    
    public void renameCategoryInCards(String oldName, String newName, List<CardItem> cardsToUpdate, @Nullable OperationCallback callback) {
        if (cardsToUpdate == null || oldName == null || newName == null || oldName.equals(newName)) {
            if (callback != null) callback.onError(new IllegalArgumentException("Invalid arguments for category rename"));
            return;
        }
        // If the list is empty, it's a valid case - just nothing to do
        if (cardsToUpdate.isEmpty()) {
            Log.d(TAG, "Repo: No cards to update for category rename: " + oldName + " -> " + newName);
            if (callback != null) callback.onSuccess();
            return;
        }
        databaseExecutor.execute(() -> {
            Log.d(TAG, "Repo: Renaming category '" + oldName + "' to '" + newName + "' in " + cardsToUpdate.size() + " cards.");
            List<CardItem> actuallyModified = new ArrayList<>();
            for (CardItem card : cardsToUpdate) {
                Set<String> cats = card.getCategories();
                if (cats != null && cats.remove(oldName)) {
                    cats.add(newName);
                    card.setCategories(cats);
                    actuallyModified.add(card);
                }
            }
            if (!actuallyModified.isEmpty()) {
                try {
                    cardDao.updateCards(actuallyModified);
                    Log.d(TAG, "Repo: Category renamed from '" + oldName + "' to '" + newName + "' in " + actuallyModified.size() + " cards.");
                    if (callback != null) callback.onSuccess();
                } catch (Exception e) {
                    Log.e(TAG, "Repo: Error updating cards after renaming category '" + oldName + "'", e);
                    if (callback != null) callback.onError(e);
                }
            } else {
                Log.d(TAG, "Repo: No cards needed updating for category rename: " + oldName);
                if (callback != null) callback.onSuccess(); // Still success if nothing needed changing
            }
        });
    }


    // --- Callback Interfaces ---
    public interface LoadCardsCallback {
        void onCardsLoaded(List<CardItem> cards);
        void onError(Exception e);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public void close() {
        Log.d(TAG, "Shutting down repository database executor.");
        databaseExecutor.shutdown();
    }
}