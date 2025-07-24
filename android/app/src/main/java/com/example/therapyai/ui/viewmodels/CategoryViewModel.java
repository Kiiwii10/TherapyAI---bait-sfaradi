package com.example.therapyai.ui.viewmodels;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.CategoryItem;
import com.example.therapyai.data.repository.CardRepository;
import com.example.therapyai.data.repository.CategoryRepository; // Import CategoryRepository
import java.util.ArrayList;
import java.util.List;

public class CategoryViewModel extends AndroidViewModel {

    private static final String TAG = "CategoryViewModel";

    private final CategoryRepository categoryRepository;
    private final CardRepository cardRepository;

    private final MutableLiveData<List<CategoryItem>> _categoryListLiveData = new MutableLiveData<>();
    public final LiveData<List<CategoryItem>> categoryListLiveData = _categoryListLiveData;

    private final MutableLiveData<List<CategoryItem>> _userCategoryListLiveData = new MutableLiveData<>();
    public final LiveData<List<CategoryItem>> userCategoryListLiveData = _userCategoryListLiveData;


    private final MutableLiveData<String> _errorLiveData = new MutableLiveData<>();
    public final LiveData<String> errorLiveData = _errorLiveData;
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>();
    public final LiveData<Boolean> isLoading = _isLoading;

    public CategoryViewModel(@NonNull Application application) {
        super(application);
        categoryRepository = CategoryRepository.getInstance(application);
        cardRepository = CardRepository.getInstance(application);
        Log.d(TAG, "ViewModel initialized.");
        // Initial load of all categories
        loadAllCategories();
    }

    /**
     * Triggers loading of all categories (including defaults like "All", "+").
     * Updates categoryListLiveData.
     */
    public void loadAllCategories() {
        Log.d(TAG, "ViewModel: Requesting load all categories.");
        _isLoading.postValue(true);
        categoryRepository.getAllCategories(new CategoryRepository.LoadCategoriesCallback() {
            @Override
            public void onCategoriesLoaded(List<CategoryItem> categories) {
                Log.d(TAG, "ViewModel: Received all categories. Count: " + categories.size());
                _categoryListLiveData.postValue(categories);
                _isLoading.postValue(false);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error loading all categories.", e);
                _errorLiveData.postValue("Error loading categories: " + e.getMessage());
                _isLoading.postValue(false);
                _categoryListLiveData.postValue(new ArrayList<>()); // Post empty list
            }
        });
    }

    /**
     * Triggers loading of only user-defined categories (excluding "All", "+").
     * Updates userCategoryListLiveData.
     */
    public void loadUserCategories() {
        Log.d(TAG, "ViewModel: Requesting load user categories.");
        _isLoading.postValue(true);
        categoryRepository.getNonDefaultCategories(new CategoryRepository.LoadCategoriesCallback() {
            @Override
            public void onCategoriesLoaded(List<CategoryItem> categories) {
                Log.d(TAG, "ViewModel: Received user categories. Count: " + categories.size());
                _userCategoryListLiveData.postValue(categories);
                _isLoading.postValue(false);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error loading user categories.", e);
                _errorLiveData.postValue("Error loading user categories: " + e.getMessage());
                _isLoading.postValue(false);
                _userCategoryListLiveData.postValue(new ArrayList<>());
            }
        });
    }

//    /**
//     * Adds a new category. Refreshes the full list afterwards.
//     * @param category The CategoryItem to add.
//     */
//    public void addCategory(CategoryItem category) {
//        Log.d(TAG, "ViewModel: Requesting add category: " + category.getName());
//        _isLoading.postValue(true);
//        categoryRepository.insertCategory(category, new CategoryRepository.OperationCallback() {
//            @Override
//            public void onSuccess() {
//                Log.d(TAG, "ViewModel: Category added successfully. Reloading list.");
//                loadAllCategories(); // Refresh the list
//            }
//            @Override
//            public void onError(Exception e) {
//                Log.e(TAG, "ViewModel: Error adding category.", e);
//                _errorLiveData.postValue("Error adding category: " + e.getMessage());
//                _isLoading.postValue(false); // Stop loading indicator on error
//            }
//        });
//    }

    /**
     * Updates an existing category. Refreshes the full list afterwards.
     * @param category The CategoryItem with updated details.
     */
    public void updateCategory(CategoryItem category) {
        Log.d(TAG, "ViewModel: Requesting update category: " + category.getName());
        _isLoading.postValue(true);
        categoryRepository.updateCategory(category, new CategoryRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Category updated successfully. Reloading list.");
                loadAllCategories(); // Refresh the list
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error updating category.", e);
                _errorLiveData.postValue("Error updating category: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * Deletes a category. Refreshes the full list afterwards.
     * NOTE: This ViewModel method does NOT handle updating associated cards.
     * That coordination should happen in the Fragment/higher-level logic.
     * @param category The CategoryItem to delete.
     */
    public void deleteCategory(CategoryItem category) {
        Log.d(TAG, "ViewModel: Requesting delete category: " + category.getName());
        if (category == null || category.isDefault()) {
            _errorLiveData.postValue("Cannot delete default categories.");
            return;
        }
        _isLoading.postValue(true);
        categoryRepository.deleteCategory(category, new CategoryRepository.OperationCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "ViewModel: Category deleted successfully. Reloading list.");
                loadAllCategories(); // Refresh the list
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "ViewModel: Error deleting category.", e);
                _errorLiveData.postValue("Error deleting category: " + e.getMessage());
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * Adds a new category after checking if it exists. Refreshes the list on success.
     * @param categoryName Name of the category to add.
     */
    public void addCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            _errorLiveData.postValue("Category name cannot be empty.");
            return;
        }
        String trimmedName = categoryName.trim();
        Log.d(TAG, "ViewModel: Requesting add category: " + trimmedName);
        _isLoading.postValue(true);

        categoryRepository.getCategoryByName(trimmedName, new CategoryRepository.LoadSingleCategoryCallback() {
            @Override
            public void onCategoryLoaded(@Nullable CategoryItem existingCategory) {
                if (existingCategory != null) {
                    _errorLiveData.postValue("Category '" + trimmedName + "' already exists.");
                    _isLoading.postValue(false);
                } else {
                    CategoryItem newCategory = new CategoryItem(trimmedName);
                    categoryRepository.insertCategory(newCategory, new CategoryRepository.OperationCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "ViewModel: Category added successfully. Reloading list.");
                            loadAllCategories();
                        }
                        @Override
                        public void onError(Exception e) {
                            handleOperationError("adding category", e);
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                handleOperationError("checking category existence", e);
            }
        });
    }

    /**
     * Updates a category name and also renames it in associated cards.
     * @param oldName The current name of the category.
     * @param newName The new name for the category.
     */
    public void renameCategory(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.trim().isEmpty() || newName.trim().isEmpty() || oldName.equalsIgnoreCase(newName.trim())) {
            _errorLiveData.postValue("Invalid names for renaming.");
            return;
        }
        String finalNewName = newName.trim();
        Log.d(TAG, "ViewModel: Requesting rename category: " + oldName + " -> " + finalNewName);
        _isLoading.postValue(true);

        // 1. Check if new name already exists
        categoryRepository.getCategoryByName(finalNewName, new CategoryRepository.LoadSingleCategoryCallback() {
            @Override
            public void onCategoryLoaded(@Nullable CategoryItem existingNew) {
                if (existingNew != null) {
                    _errorLiveData.postValue("Category '" + finalNewName + "' already exists.");
                    _isLoading.postValue(false);
                    return;
                }

                // 2. Get cards associated with the old name (using CardRepo)
                cardRepository.loadCardsByCategory(oldName, new CardRepository.LoadCardsCallback() {
                    @Override
                    public void onCardsLoaded(List<CardItem> cardsToUpdate) {
                        // 3. Tell CardRepo to rename category within those cards
                        cardRepository.renameCategoryInCards(oldName, finalNewName, cardsToUpdate, new CardRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                // 4. If card update succeeds, RENAME the category item itself in the database
                                categoryRepository.renameCategoryInDb(oldName, finalNewName, new CategoryRepository.OperationCallback() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "ViewModel: Category rename fully successful. Reloading categories.");
                                        loadAllCategories(); // Reload category list (triggers loading=false)
                                    }
                                    @Override
                                    public void onError(Exception e) { handleOperationError("renaming category item itself", e); }
                                });
                            }
                            @Override
                            public void onError(Exception e) { handleOperationError("renaming category in cards", e); }
                        });
                    }
                    @Override
                    public void onError(Exception e) { handleOperationError("fetching cards for category rename", e); }
                });
            }
            @Override
            public void onError(Exception e) { handleOperationError("checking new category name existence", e); }
        });
    }


    /**
     * Deletes a category and removes it from associated cards.
     * @param categoryToDelete The category to delete.
     */
    public void deleteCategoryAndAssociations(CategoryItem categoryToDelete) {
        if (categoryToDelete == null || categoryToDelete.isDefault()) {
            _errorLiveData.postValue("Cannot delete default categories.");
            return;
        }
        String categoryName = categoryToDelete.getName();
        Log.d(TAG, "ViewModel: Requesting delete category and associations: " + categoryName);
        _isLoading.postValue(true);

        // 1. Get cards associated with the category
        cardRepository.loadCardsByCategory(categoryName, new CardRepository.LoadCardsCallback() {
            @Override
            public void onCardsLoaded(List<CardItem> cardsToUpdate) {
                // 2. Tell CardRepo to remove the category from those cards
                cardRepository.removeCategoryFromCards(categoryName, cardsToUpdate, new CardRepository.OperationCallback() {
                    @Override
                    public void onSuccess() {
                        // 3. If card update succeeds, delete the category itself
                        categoryRepository.deleteCategory(categoryToDelete, new CategoryRepository.OperationCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "ViewModel: Category deletion and association removal successful. Reloading categories.");
                                loadAllCategories(); // Reload category list (triggers loading=false)
                            }
                            @Override
                            public void onError(Exception e) { handleOperationError("deleting category item itself", e); }
                        });
                    }
                    @Override
                    public void onError(Exception e) { handleOperationError("removing category from cards", e); }
                });
            }
            @Override
            public void onError(Exception e) { handleOperationError("fetching cards for category deletion", e); }
        });
    }


    // Check if category name exists (useful before adding/renaming)
    // This requires a synchronous check or another callback structure
    // For simplicity, keeping this synchronous (use with caution or refactor)
    // public boolean categoryExists(String name) {
    //    // Note: This is synchronous - only call from background thread if possible!
    //    // Or refactor repository method to be synchronous for this specific check.
    //    try {
    //        return categoryRepository.getCategoryByNameSync(name) != null; // Need sync method in repo/DAO
    //    } catch (Exception e) {
    //        Log.e(TAG, "Error checking if category exists", e);
    //        return true; // Assume exists on error to prevent duplicates
    //    }
    // }


    // Helper for consistent error handling
    private void handleOperationError(String operation, Exception e) {
        Log.e(TAG, "ViewModel: Error " + operation + ".", e);
        _errorLiveData.postValue("Error " + operation + ": " + e.getMessage());
        _isLoading.postValue(false);
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "ViewModel cleared.");
    }
}