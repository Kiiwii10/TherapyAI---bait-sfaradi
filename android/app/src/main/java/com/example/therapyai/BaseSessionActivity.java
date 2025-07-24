package com.example.therapyai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.util.AppStateTracker;

import org.jetbrains.annotations.Nullable;

public abstract class BaseSessionActivity extends AppCompatActivity {
    private static final String TAG = "BaseSessionActivity";
    
    // Session refresh mechanism
    private static final long SESSION_REFRESH_INTERVAL = 3 * 60 * 1000; // 3 minutes (more frequent to ensure session stays alive)
    private Handler sessionRefreshHandler;
    private Runnable sessionRefreshRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the activity to be secure - no screenshots or screen sharing
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
    }    @Override
    protected void onResume() {
        super.onResume();
        
        // DIAGNOSTIC: Log session activity lifecycle
        Log.d(TAG, "DIAGNOSTIC: BaseSessionActivity.onResume() - " + this.getClass().getSimpleName());
        Log.d(TAG, "DIAGNOSTIC: Task ID: " + getTaskId());
        Log.d(TAG, "DIAGNOSTIC: Current time: " + System.currentTimeMillis());
        
        // Pause the inactivity timer while this form is active
        Log.d(TAG, "onResume: Pausing session inactivity timer.");
        Log.d(TAG, "DIAGNOSTIC: Before pause - Session valid: " + SessionManager.getInstance().isSessionValid());
        Log.d(TAG, "DIAGNOSTIC: Before pause - Last activity time: " + SessionManager.getInstance().getLastActivityTime());
        SessionManager.getInstance().pauseInactivityTimer();
        
        // Notify AppStateTracker that we're in a session activity
        AppStateTracker.getInstance().enterSessionState();
        
        // Start background session refresh to prevent timeout
        startSessionRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // DIAGNOSTIC: Log session activity lifecycle
        Log.d(TAG, "DIAGNOSTIC: BaseSessionActivity.onPause() - " + this.getClass().getSimpleName());
        Log.d(TAG, "DIAGNOSTIC: Task ID: " + getTaskId());
        Log.d(TAG, "DIAGNOSTIC: Current time: " + System.currentTimeMillis());
        Log.d(TAG, "DIAGNOSTIC: Is finishing: " + isFinishing());
        
        // Resume the inactivity timer when leaving this form
        Log.d(TAG, "onPause: Resuming session inactivity timer.");
        Log.d(TAG, "DIAGNOSTIC: Before resume - Session valid: " + SessionManager.getInstance().isSessionValid());
        Log.d(TAG, "DIAGNOSTIC: Before resume - Last activity time: " + SessionManager.getInstance().getLastActivityTime());
        SessionManager.getInstance().resumeInactivityTimer();
        Log.d(TAG, "DIAGNOSTIC: After resume - Last activity time: " + SessionManager.getInstance().getLastActivityTime());
        
        // Notify AppStateTracker that we're leaving the session activity
        AppStateTracker.getInstance().exitSessionState();
        
        // Stop background session refresh
        stopSessionRefresh();
    }
    
    /**
     * Start background session refresh to keep the session alive while user is actively using the activity
     */
    private void startSessionRefresh() {
        Log.d(TAG, "Starting background session refresh every " + (SESSION_REFRESH_INTERVAL / 1000) + " seconds");
        
        if (sessionRefreshHandler == null) {
            sessionRefreshHandler = new Handler(Looper.getMainLooper());
        }
        
        sessionRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                // Refresh the session timer to prevent timeout
                Log.d(TAG, "Background session refresh - updating last activity time");
                SessionManager.getInstance().updateLastActivityTime();
                
                // Schedule next refresh
                sessionRefreshHandler.postDelayed(this, SESSION_REFRESH_INTERVAL);
            }
        };
        
        // Start the first refresh
        sessionRefreshHandler.postDelayed(sessionRefreshRunnable, SESSION_REFRESH_INTERVAL);
    }
    
    /**
     * Stop the background session refresh
     */
    private void stopSessionRefresh() {
        Log.d(TAG, "Stopping background session refresh");
        
        if (sessionRefreshHandler != null && sessionRefreshRunnable != null) {
            sessionRefreshHandler.removeCallbacks(sessionRefreshRunnable);
            sessionRefreshRunnable = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Ensure we clean up the session refresh handler
        stopSessionRefresh();
    }
}
