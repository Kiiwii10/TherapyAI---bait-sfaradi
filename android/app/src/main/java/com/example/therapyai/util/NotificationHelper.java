package com.example.therapyai.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.therapyai.R;
import com.example.therapyai.data.local.LocalStorageManager;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "therapy_ai_channel";
    private static final String CHANNEL_NAME = "Therapy AI Notifications";
    private static final String CHANNEL_DESC = "Notifications from Therapy AI";
    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private final LocalStorageManager localStorageManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext(); // Use application context
        this.notificationManager = NotificationManagerCompat.from(this.context);
        this.localStorageManager = LocalStorageManager.getInstance(); // Get instance
        createNotificationChannel(); // Ensure channel exists
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription(CHANNEL_DESC);

            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created or already exists.");
            } else {
                Log.e(TAG, "Failed to get NotificationManager service.");
            }
        }
    }

    // Check if notifications are enabled for the app AND our specific channel
    public boolean areNotificationsEnabledForChannel() {
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled for the app at the system level.");
            return false;
        }
        // Channel importance check is only relevant for API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            // Channel might not exist if never used, or might be blocked
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "Notifications are disabled for the specific channel: " + CHANNEL_ID);
                return false;
            } else if (channel == null) {
                // Should not happen if createNotificationChannel was called, but check anyway
                Log.w(TAG, "Notification channel " + CHANNEL_ID + " not found/yet created.");

                return true; // Let's assume it will be created when needed.
            }
        }
        return true; // Notifications seem enabled
    }
    /**
     * Shows a notification using the settings from LocalStorageManager.
     * If system notifications are disabled, falls back to in-app notifications.
     *
     * @param title Title of the notification.
     * @param messageBody Body text of the notification.
     * @param pendingIntent Intent to launch when the notification is tapped.
     */
    public void showNotificationWithPendingIntent(String title, String messageBody, PendingIntent pendingIntent) {
        showNotificationWithPendingIntent(title, messageBody, pendingIntent, null, null);
    }

    /**
     * Shows a notification using the settings from LocalStorageManager.
     * If system notifications are disabled, falls back to in-app notifications.
     *
     * @param title Title of the notification.
     * @param messageBody Body text of the notification.
     * @param pendingIntent Intent to launch when the notification is tapped.
     * @param notificationType Type of notification (e.g., "SESSION_READY")
     * @param dataId Optional data ID for the notification
     */
    public void showNotificationWithPendingIntent(String title, String messageBody, PendingIntent pendingIntent, 
                                                 String notificationType, String dataId) {        // Check 1: System/Channel enabled status (includes implicit permission check)
        if (!areNotificationsEnabledForChannel()) {
            Log.w(TAG, "Notifications are disabled via system/channel settings, using in-app notification fallback.");
            
            // Use in-app notification fallback
            try {
                InAppNotificationManager inAppManager = InAppNotificationManager.getInstance();
                // Since PendingIntent doesn't expose the wrapped Intent, we'll need to create
                // a generic intent or pass null - the in-app notification can handle this
                Intent fallbackIntent = null;
                if (pendingIntent != null) {
                    // We can't extract the original intent from PendingIntent
                    // The in-app notification will work without a specific intent
                    Log.d(TAG, "PendingIntent provided but cannot extract Intent for in-app notification");
                }
                inAppManager.showInAppNotification(title, messageBody, fallbackIntent, notificationType, dataId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show in-app notification fallback", e);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                Log.e(TAG, "POST_NOTIFICATIONS permission is missing, cannot show notification. " +
                        "Ensure permission is requested in the foreground Activity.");
                return;
            }
        }

        // --- Permission seems granted, proceed ---

        boolean vibrateEnabled = localStorageManager.isNotificationVibrationEnabled();
        boolean soundEnabled = localStorageManager.isNotificationSoundEnabled();
        boolean popupEnabled = localStorageManager.isNotificationPopupEnabled();
        int priority = popupEnabled ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(priority)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (soundEnabled) {
            notificationBuilder.setSound(LocalStorageManager.getDefaultNotificationSoundUri());
        } else {
            notificationBuilder.setSound(null);
        }

        if (vibrateEnabled) {
            notificationBuilder.setVibrate(new long[]{0}); // Default pattern
        } else {
            notificationBuilder.setVibrate(null);
        }

        int notificationId = 1;

        try {
            Log.d(TAG, "Attempting to show notification (ID: " + notificationId + ") with Vibrate=" + vibrateEnabled + ", Sound=" + soundEnabled);
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.i(TAG, "Notification successfully posted (ID: " + notificationId + ")");
        } catch (SecurityException se) {

            Log.e(TAG, "SecurityException while posting notification (ID: " + notificationId + "). " +
                    "This indicates a potential permission issue despite checks.", se);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification (ID: " + notificationId + ") due to an unexpected error.", e);
        }
    }

    /**
     * Shows a notification using the settings from LocalStorageManager.
     * If system notifications are disabled, falls back to in-app notifications.
     *
     * @param title Title of the notification.
     * @param messageBody Body text of the notification.
     * @param pendingIntent Intent to launch when the notification is tapped.
     * @param fallbackIntent Original intent for in-app notification fallback (since PendingIntent doesn't expose the wrapped Intent)
     * @param notificationType Type of notification (e.g., "SESSION_READY")
     * @param dataId Optional data ID for the notification
     */
    public void showNotificationWithPendingIntent(String title, String messageBody, PendingIntent pendingIntent, 
                                                 Intent fallbackIntent, String notificationType, String dataId) {

        // Check 1: System/Channel enabled status (includes implicit permission check)
        if (!areNotificationsEnabledForChannel()) {
            Log.w(TAG, "Notifications are disabled via system/channel settings, using in-app notification fallback.");
            
            // Use in-app notification fallback with the provided fallback intent
            try {
                InAppNotificationManager inAppManager = InAppNotificationManager.getInstance();
                inAppManager.showInAppNotification(title, messageBody, fallbackIntent, notificationType, dataId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to show in-app notification fallback", e);
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                Log.e(TAG, "POST_NOTIFICATIONS permission is missing, cannot show notification. " +
                        "Ensure permission is requested in the foreground Activity.");
                return;
            }
        }

        // --- Permission seems granted, proceed ---
        showSystemNotification(title, messageBody, pendingIntent);
    }

    /**
     * Internal method to show system notification with current settings.
     */
    private void showSystemNotification(String title, String messageBody, PendingIntent pendingIntent) {
        boolean vibrateEnabled = localStorageManager.isNotificationVibrationEnabled();
        boolean soundEnabled = localStorageManager.isNotificationSoundEnabled();
        boolean popupEnabled = localStorageManager.isNotificationPopupEnabled();
        int priority = popupEnabled ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(priority)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (soundEnabled) {
            notificationBuilder.setSound(LocalStorageManager.getDefaultNotificationSoundUri());
        } else {
            notificationBuilder.setSound(null);
        }

        if (vibrateEnabled) {
            notificationBuilder.setVibrate(new long[]{0}); // Default pattern
        } else {
            notificationBuilder.setVibrate(null);
        }

        int notificationId = 1;

        try {
            Log.d(TAG, "Attempting to show notification (ID: " + notificationId + ") with Vibrate=" + vibrateEnabled + ", Sound=" + soundEnabled);
            notificationManager.notify(notificationId, notificationBuilder.build());
            Log.i(TAG, "Notification successfully posted (ID: " + notificationId + ")");
        } catch (SecurityException se) {

            Log.e(TAG, "SecurityException while posting notification (ID: " + notificationId + "). " +
                    "This indicates a potential permission issue despite checks.", se);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification (ID: " + notificationId + ") due to an unexpected error.", e);
        }
    }
}