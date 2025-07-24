package com.example.therapyai;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.LocalStorageManager;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.local.dao.CardDao;
import com.example.therapyai.data.local.dao.CategoryDao;
import com.example.therapyai.data.local.db.AppDatabase;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.CategoryItem;
import com.example.therapyai.data.repository.NotificationRepository;
import com.example.therapyai.data.repository.AuthRepository;
import com.example.therapyai.data.repository.ProcessedDataRepository;
import com.example.therapyai.data.repository.RecordingRepository;
import com.example.therapyai.data.repository.SearchRepository;
import com.example.therapyai.util.HIPAAKeyManager;
import com.example.therapyai.util.InAppNotificationManager;
import com.google.firebase.FirebaseApp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TherapyAIApp extends Application {
    private static final String TAG = "TherapyAIApp";
    private static TherapyAIApp instance;
    private AppDatabase db;
    private final ExecutorService databaseInitExecutor = Executors.newSingleThreadExecutor();


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.i(TAG, "Application starting. Attempting to delete any existing HIPAA key...");
        try {
            HIPAAKeyManager.deleteKey();
            Log.i(TAG, "Pre-emptive key deletion attempt completed.");
        } catch (Exception e) {
            Log.e(TAG, "Error during pre-emptive key deletion on startup. Continuing...", e);
        }

        Log.i(TAG, "Attempting FirebaseApp.initializeApp...");
        boolean firebaseInitialized = false;
        try {
            FirebaseApp.initializeApp(this);
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.e(TAG, "FirebaseApp.initializeApp completed but getApps is empty! Initialization likely failed.");
            } else {
                Log.i(TAG, "FirebaseApp.initializeApp completed successfully (or already initialized).");
                firebaseInitialized = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL ERROR initializing Firebase", e);
        }
        Log.i(TAG, "Firebase Initialization Attempt Complete. Success = " + firebaseInitialized);        
        SessionManager.init(this);
        EphemeralPrefs.init(this);
        LocalStorageManager.init(this);        LocalStorageManager.getInstance().applyThemeFromPreferences();

        initRepositories(false); // TODO: change to false when adding api source

        // Initialize InAppNotificationManager (after repositories are initialized)
        InAppNotificationManager.getInstance(this);

        db = AppDatabase.getDatabase(this);

        addDefaultEntries();

    }

    public static TherapyAIApp getInstance() {
        return instance;
    }

    public AppDatabase getDb() {
        return db;
    }

    private void addDefaultEntries() {
        databaseInitExecutor.execute(() -> {
            Log.d(TAG, "Checking/Adding Default DB Entries...");
            try {
                CategoryDao categoryDao = db.categoryDao();
                if (categoryDao.getAllCategories().isEmpty()) {
                    Log.d(TAG, "Adding default categories...");
                    categoryDao.insertAll(
                            new CategoryItem("All"),
                            new CategoryItem("Dialog"),
                            new CategoryItem("VR"),
                            new CategoryItem("+")
                    );
                } else {
                    Log.d(TAG, "Default categories already exist.");
                }

                CardDao cardDao = db.cardDao();
                if (cardDao.getAllCards().isEmpty()) {
                    Log.d(TAG, "Adding default card item...");
                    Set<String> defaultCategories = new HashSet<>();

                    CardItem defaultCard = new CardItem("Default Audio", "Default description");
                    defaultCard.setCategories(defaultCategories);
                    defaultCard.setType("Default Audio");
                    cardDao.insertCard(defaultCard);
                } else {
                    Log.d(TAG, "Default card(s) already exist.");
                }
                Log.d(TAG, "Default DB Entry check complete.");
            } catch (Exception e) {
                Log.e(TAG, "Error adding default DB entries", e);
            }
        });
    }

    private void initRepositories(boolean useMockData) {
        AuthRepository.init(useMockData);
        SearchRepository.init(useMockData);
        RecordingRepository.init(useMockData);
        ProcessedDataRepository.init(useMockData);
        NotificationRepository.init(useMockData);
    }

}
