package com.example.therapyai.data.repository; // Or your preferred package

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.therapyai.TherapyAIApp;
import com.example.therapyai.data.local.dao.CategoryDao;
import com.example.therapyai.data.local.db.AppDatabase;
import com.example.therapyai.data.local.models.CategoryItem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryRepository {

    private static final String TAG = "CategoryRepository";

    private final CategoryDao categoryDao;
    private final ExecutorService databaseExecutor;

    // --- Singleton Pattern ---
    private static volatile CategoryRepository INSTANCE;

    public static CategoryRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (CategoryRepository.class) {
                if (INSTANCE == null) {
                    AppDatabase database = TherapyAIApp.getInstance().getDb();
                    INSTANCE = new CategoryRepository(database.categoryDao());
                }
            }
        }
        return INSTANCE;
    }
    // --- End Singleton ---

    private CategoryRepository(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
        this.databaseExecutor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "Repository initialized.");
    }

    // --- Public Data Access Methods ---

    /**
     * Renames a category directly in the database.
     * @param oldName The current name of the category.
     * @param newName The new name for the category.
     * @param callback Optional callback for success/failure.
     */
    public void renameCategoryInDb(String oldName, String newName, @Nullable OperationCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                // Note: This only changes the name. If other attributes like 'isDefault' or 'position'
                // need to change based on the name change (which they shouldn't for a simple rename),
                // that logic would be more complex. For now, this just renames.
                int rowsAffected = categoryDao.renameCategoryValues(oldName, newName);
                Log.d(TAG, "Repo: Renamed category in DB from '" + oldName + "' to '" + newName + "'. Rows affected: " + rowsAffected);
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error renaming category in DB: " + oldName + " -> " + newName, e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Gets all categories (including defaults) asynchronously.
     * @param callback Callback to handle the result list or error.
     */
    public void getAllCategories(@NonNull LoadCategoriesCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                List<CategoryItem> categories = categoryDao.getAllCategories();
                callback.onCategoriesLoaded(categories != null ? categories : new ArrayList<>());
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error loading all categories", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Gets only user-defined (non-default) categories asynchronously.
     * @param callback Callback to handle the result list or error.
     */
    public void getNonDefaultCategories(@NonNull LoadCategoriesCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                List<CategoryItem> categories = categoryDao.getNonDefaultCategories();
                callback.onCategoriesLoaded(categories != null ? categories : new ArrayList<>());
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error loading non-default categories", e);
                callback.onError(e);
            }
        });
    }

    /**
     * Gets a specific category by name asynchronously.
     * @param name Name of the category.
     * @param callback Callback with the single category or error/not found.
     */
    public void getCategoryByName(String name, @NonNull LoadSingleCategoryCallback callback) {
        if (name == null || name.isEmpty()) {
            callback.onError(new IllegalArgumentException("Category name cannot be empty"));
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                CategoryItem category = categoryDao.getCategoryByName(name);
                callback.onCategoryLoaded(category); // Can be null if not found
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error getting category by name: " + name, e);
                callback.onError(e);
            }
        });
    }


    /**
     * Inserts a new category asynchronously.
     * @param category The category to insert.
     * @param callback Optional callback for success/failure.
     */
    public void insertCategory(CategoryItem category, @Nullable OperationCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                // Optional: Check if category with same name already exists before inserting
                // CategoryItem existing = categoryDao.getCategoryByName(category.getName());
                // if (existing != null) {
                //    throw new IllegalStateException("Category already exists");
                // }
                categoryDao.insertCategory(category);
                Log.d(TAG, "Repo: Inserted category: " + category.getName());
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error inserting category: " + category.getName(), e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Updates an existing category asynchronously.
     * @param category The category with updated details.
     * @param callback Optional callback for success/failure.
     */
    public void updateCategory(CategoryItem category, @Nullable OperationCallback callback) {
        databaseExecutor.execute(() -> {
            try {
                categoryDao.updateCategory(category);
                Log.d(TAG, "Repo: Updated category: " + category.getName());
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error updating category: " + category.getName(), e);
                if (callback != null) callback.onError(e);
            }
        });
    }

    /**
     * Deletes a category asynchronously.
     * Note: This does NOT handle updating associated CardItems. That logic
     * should be coordinated by the ViewModel or calling component.
     * @param category The category to delete.
     * @param callback Optional callback for success/failure.
     */
    public void deleteCategory(CategoryItem category, @Nullable OperationCallback callback) {
        // Add checks: Don't delete default 'All' or '+' categories
        if (category == null || category.isDefault()) {
            String message = "Cannot delete null or default category";
            Log.w(TAG, message + (category != null ? ": "+category.getName() : ""));
            if (callback != null) callback.onError(new IllegalArgumentException(message));
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                categoryDao.deleteCategory(category);
                Log.d(TAG, "Repo: Deleted category: " + category.getName());
                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Repo: Error deleting category: " + category.getName(), e);
                if (callback != null) callback.onError(e);
            }
        });
    }


    // --- Callback Interfaces ---
    public interface LoadCategoriesCallback {
        void onCategoriesLoaded(List<CategoryItem> categories);
        void onError(Exception e);
    }

    public interface LoadSingleCategoryCallback {
        void onCategoryLoaded(@Nullable CategoryItem category);
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