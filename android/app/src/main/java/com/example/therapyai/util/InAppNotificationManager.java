package com.example.therapyai.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.therapyai.BaseSessionActivity;
import com.example.therapyai.R;
import com.example.therapyai.data.local.LocalStorageManager;
import com.example.therapyai.data.repository.ProcessedDataRepository;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages in-app notifications when system notifications are disabled.
 * Provides fallback notification experience with audio, visual alerts, and badge updates.
 */
public class InAppNotificationManager implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
      private static final String TAG = "InAppNotificationManager";
    private static final long DEFAULT_NOTIFICATION_DISPLAY_DURATION = 5000; // 5 seconds
    
    private static InAppNotificationManager instance;
    private final Context applicationContext;
    private final LocalStorageManager localStorageManager;
    private final ProcessedDataRepository processedDataRepository;
    private final Handler mainHandler;
    private final AtomicBoolean isAppInForeground = new AtomicBoolean(false);
    
    // Settings for in-app notifications
    private volatile boolean inAppNotificationsEnabled = true;
    private volatile boolean inAppSoundEnabled = true;
    private volatile long autoDismissTime = DEFAULT_NOTIFICATION_DISPLAY_DURATION;
    
    // Current activity reference
    private WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    
    // Notification queue and display state
    private volatile boolean isNotificationCurrentlyDisplayed = false;
      private InAppNotificationManager(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.localStorageManager = LocalStorageManager.getInstance();
        this.processedDataRepository = ProcessedDataRepository.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Load settings from storage
        loadSettings();
        
        // Register for activity lifecycle callbacks
        if (applicationContext instanceof Application) {
            ((Application) applicationContext).registerActivityLifecycleCallbacks(this);
        }
        
        // Register for process lifecycle to track app foreground/background state
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }
    
    public static synchronized InAppNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new InAppNotificationManager(context);
        }
        return instance;
    }
    
    public static InAppNotificationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("InAppNotificationManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }
    
    /**
     * Shows an in-app notification as a fallback when system notifications are disabled.
     * 
     * @param title The notification title
     * @param message The notification message
     * @param intent The intent to launch when notification is tapped
     * @param notificationType The type of notification (e.g., "SESSION_READY")
     * @param dataId Optional data ID for the notification
     */    public void showInAppNotification(String title, String message, Intent intent, 
                                    String notificationType, String dataId) {
        Log.d(TAG, "showInAppNotification called: " + title);
        
        // Check if in-app notifications are enabled
        if (!inAppNotificationsEnabled) {
            Log.d(TAG, "In-app notifications disabled, skipping");
            return;
        }
        
        // Update pending data count first
        updatePendingDataCount();
        
        // Only show visual notification if app is in foreground
        if (isAppInForeground.get()) {
            showVisualNotification(title, message, intent);
        } else {
            Log.d(TAG, "App in background, skipping visual notification");
        }
        
        // Handle audio notification based on session state
        handleAudioNotification();
        
        // Log the notification for debugging
        Log.i(TAG, "In-app notification displayed: " + title + " | " + message);
    }
    
    /**
     * Updates the pending data count and notifies relevant components.
     */
    private void updatePendingDataCount() {
        try {
            // Refresh pending data count
            processedDataRepository.refreshPendingData();
            
            // Update badge count if needed
            updateBadgeCount();
            
            Log.d(TAG, "Pending data count updated");
        } catch (Exception e) {
            Log.e(TAG, "Error updating pending data count", e);
        }
    }
    
    /**
     * Updates application badge count (if supported by launcher).
     */
    private void updateBadgeCount() {
        // Android doesn't have a standard badge API, but some launchers support it
        // This is a placeholder for potential badge update implementation
        // Could use libraries like ShortcutBadger if needed
        Log.d(TAG, "Badge count update placeholder");
    }
    
    /**
     * Shows visual in-app notification overlay.
     */
    private void showVisualNotification(String title, String message, Intent intent) {
        Activity currentActivity = currentActivityRef.get();
        if (currentActivity == null || currentActivity.isFinishing()) {
            Log.w(TAG, "No valid activity to show visual notification");
            return;
        }
        
        if (isNotificationCurrentlyDisplayed) {
            Log.d(TAG, "Notification already displayed, skipping");
            return;
        }
        
        mainHandler.post(() -> {
            try {
                showNotificationOverlay(currentActivity, title, message, intent);
            } catch (Exception e) {
                Log.e(TAG, "Error showing visual notification", e);
                // Fallback to toast
                showToastFallback(title, message);
            }
        });
    }
    
    /**
     * Shows notification as an overlay view.
     */
    private void showNotificationOverlay(Activity activity, String title, String message, Intent intent) {
        isNotificationCurrentlyDisplayed = true;
        
        // Get the root view of the activity
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        if (rootView == null) {
            showToastFallback(title, message);
            return;
        }
        
        // Inflate notification layout
        LayoutInflater inflater = LayoutInflater.from(activity);
        View notificationView = inflater.inflate(R.layout.in_app_notification, rootView, false);
        
        // Set up notification content
        TextView titleView = notificationView.findViewById(R.id.notification_title);
        TextView messageView = notificationView.findViewById(R.id.notification_message);
        View dismissButton = notificationView.findViewById(R.id.notification_dismiss);
        
        titleView.setText(title);
        messageView.setText(message);
        
        // Set up click listeners
        notificationView.setOnClickListener(v -> {
            removeNotificationView(rootView, notificationView);
            if (intent != null) {
                try {
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error launching intent from notification", e);
                }
            }
        });
        
        dismissButton.setOnClickListener(v -> {
            removeNotificationView(rootView, notificationView);
        });
        
        // Add to root view
        rootView.addView(notificationView);
          // Auto-dismiss after timeout
        mainHandler.postDelayed(() -> {
            removeNotificationView(rootView, notificationView);
        }, autoDismissTime);
    }
    
    /**
     * Removes notification view from the root view.
     */
    private void removeNotificationView(ViewGroup rootView, View notificationView) {
        try {
            if (notificationView.getParent() != null) {
                rootView.removeView(notificationView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing notification view", e);
        } finally {
            isNotificationCurrentlyDisplayed = false;
        }
    }
    
    /**
     * Shows a toast as fallback when overlay fails.
     */
    private void showToastFallback(String title, String message) {
        String toastMessage = title + ": " + message;
        Toast.makeText(applicationContext, toastMessage, Toast.LENGTH_LONG).show();
        isNotificationCurrentlyDisplayed = false;
    }
    
    /**
     * Handles audio notification based on current session state and user preferences.
     */    private void handleAudioNotification() {
        try {
            // Check if in-app sound is enabled
            if (!inAppSoundEnabled) {
                Log.d(TAG, "In-app notification sound disabled in settings");
                return;
            }
            
            // Check if sound is enabled in general settings
            if (!localStorageManager.isNotificationSoundEnabled()) {
                Log.d(TAG, "Notification sound disabled in settings");
                return;
            }
            
            // Check if user is in a session activity (no sound during sessions)
            if (AppStateTracker.getInstance().isInSession()) {
                Log.d(TAG, "User in session, skipping audio notification");
                return;
            }
            
            // Check if current activity is a BaseSessionActivity
            Activity currentActivity = currentActivityRef.get();
            if (currentActivity instanceof BaseSessionActivity) {
                Log.d(TAG, "Current activity is BaseSessionActivity, skipping audio notification");
                return;
            }
            
            // Play notification sound
            playNotificationSound();
            
            // Handle vibration if enabled
            if (localStorageManager.isNotificationVibrationEnabled()) {
                // Note: Vibration would require VIBRATE permission
                Log.d(TAG, "Vibration enabled but not implemented in fallback");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling audio notification", e);
        }
    }
    
    /**
     * Plays notification sound.
     */
    private void playNotificationSound() {
        try {
            Uri soundUri = LocalStorageManager.getDefaultNotificationSoundUri();
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            
            Ringtone ringtone = RingtoneManager.getRingtone(applicationContext, soundUri);
            if (ringtone != null) {
                // Check if device is not in silent mode
                AudioManager audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null && audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    ringtone.play();
                    Log.d(TAG, "Played notification sound");
                } else {
                    Log.d(TAG, "Device in silent mode, skipping sound");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }
    
    // Application.ActivityLifecycleCallbacks implementation
    
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Not needed
    }
    
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // Not needed
    }
    
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
        Log.d(TAG, "Activity resumed: " + activity.getClass().getSimpleName());
    }
    
    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // Clear current activity reference when paused
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }
    
    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // Not needed
    }
    
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // Not needed
    }
    
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // Clear reference if this activity is destroyed
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }
    
    // Settings management methods
    
    /**
     * Loads settings from LocalStorageManager.
     */
    private void loadSettings() {
        inAppNotificationsEnabled = localStorageManager.getBoolean("in_app_notifications_enabled", true);
        inAppSoundEnabled = localStorageManager.getBoolean("in_app_notification_sound_enabled", true);
        int autoDismissSeconds = localStorageManager.getInt("in_app_notification_auto_dismiss_time", 5);
        autoDismissTime = autoDismissSeconds * 1000L; // Convert to milliseconds
    }
    
    /**
     * Sets whether in-app notifications are enabled.
     */
    public void setInAppNotificationsEnabled(boolean enabled) {
        this.inAppNotificationsEnabled = enabled;
        Log.d(TAG, "In-app notifications enabled: " + enabled);
    }
    
    /**
     * Sets whether in-app notification sound is enabled.
     */
    public void setInAppSoundEnabled(boolean enabled) {
        this.inAppSoundEnabled = enabled;
        Log.d(TAG, "In-app notification sound enabled: " + enabled);
    }
    
    /**
     * Sets the auto-dismiss time for in-app notifications.
     * 
     * @param timeInMillis Time in milliseconds before auto-dismissing notifications
     */
    public void setAutoDismissTime(long timeInMillis) {
        this.autoDismissTime = timeInMillis;
        Log.d(TAG, "Auto-dismiss time set to: " + timeInMillis + "ms");
    }
    
    /**
     * Gets whether in-app notifications are enabled.
     */
    public boolean isInAppNotificationsEnabled() {
        return inAppNotificationsEnabled;
    }
    
    /**
     * Gets whether in-app notification sound is enabled.
     */
    public boolean isInAppSoundEnabled() {
        return inAppSoundEnabled;
    }
    
    /**
     * Gets the auto-dismiss time in milliseconds.
     */
    public long getAutoDismissTime() {
        return autoDismissTime;
    }
    
    // ProcessLifecycleOwner.LifecycleObserver implementation
    
    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isAppInForeground.set(true);
        Log.d(TAG, "App moved to foreground");
    }
    
    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isAppInForeground.set(false);
        Log.d(TAG, "App moved to background");
    }
    
    /**
     * Checks if the app is currently in the foreground.
     */
    public boolean isAppInForeground() {
        return isAppInForeground.get();
    }
    
    /**
     * Gets the current foreground activity.
     */
    @Nullable
    public Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
}
