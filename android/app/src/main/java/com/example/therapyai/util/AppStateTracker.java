package com.example.therapyai.util;

import android.util.Log;

public class AppStateTracker {
    private static final String TAG = "AppStateTracker";
    private static AppStateTracker instance;
    private volatile boolean isInSession = false;

    private AppStateTracker() {}

    public static synchronized AppStateTracker getInstance() {
        if (instance == null) {
            instance = new AppStateTracker();
        }
        return instance;
    }

    public boolean isInSession() {
        return isInSession;
    }

    public void enterSessionState() {
        Log.d(TAG, "Entering session state");
        isInSession = true;
    }

    public void exitSessionState() {
        Log.d(TAG, "Exiting session state");
        isInSession = false;
    }
}