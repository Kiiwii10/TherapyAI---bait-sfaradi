package com.example.therapyai.data.remote;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.therapyai.R;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.repository.NotificationRepository;
import com.example.therapyai.ui.MainActivity;
import com.example.therapyai.ui.browse.InboxActivity;
import com.example.therapyai.ui.browse.ProcessedDataDetailActivity;
import com.example.therapyai.util.NotificationHelper;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class NotificationService extends FirebaseMessagingService {
    private static final String TAG = "NotificationService";
    private static final String CHANNEL_ID = "therapy_ai_channel";
    private static final String CHANNEL_NAME = "Therapy AI Notifications";
    private static final String CHANNEL_DESC = "Notifications from Therapy AI";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if user is signed in - only process notifications if logged in
        if (SessionManager.getInstance().isSessionValid()) {
            handleNotification(remoteMessage);
        } else {
            // User not signed in, ignore the notification
            Log.d(TAG, "User not signed in, ignoring notification");
        }
    }

    private void handleNotification(RemoteMessage remoteMessage) {
        String title = null;
        String body = null;
        String dataId = null;
        String notificationType = null;

        // Prioritize data payload
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Message Data Payload: " + remoteMessage.getData());
            title = remoteMessage.getData().get("title");
            body = remoteMessage.getData().get("body");
            dataId = remoteMessage.getData().get("dataId");
            notificationType = remoteMessage.getData().get("notification_type");        } else if (remoteMessage.getNotification() != null) { // Fallback to notification payload
            Log.d(TAG, "Message Notification Payload: " + remoteMessage.getNotification().getBody());
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // Use default title/body if none provided
        if (title == null) title = getString(R.string.app_name);
        if (body == null) body = "You have a new notification.";
        
        // Set specific message for SESSION_READY notifications
        if ("SESSION_READY".equals(notificationType)) {
            if (body == null || body.equals("You have a new notification.")) {
                body = "Session ready for processing";
            }
        }



        Intent intent;
        PendingIntent pendingIntent;
        int requestCode = (dataId != null) ? dataId.hashCode() : 0;

        if ("SESSION_READY".equals(notificationType)) {
            intent = new Intent(this, InboxActivity.class); // Target InboxActivity
            intent.putExtra("notification_source", "SESSION_READY");
            if (dataId != null) {
                intent = new Intent(this, ProcessedDataDetailActivity.class);
                intent.putExtra("notification_source", notificationType);
                intent.putExtra("data_id", dataId);
            }
            else{
                intent = new Intent(this, InboxActivity.class);
                intent.putExtra("notification_source", notificationType);
            }            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            // Default to MainActivity
            intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        // Call the specific helper method with the created PendingIntent
        NotificationHelper notificationHelper = new NotificationHelper(this);
        notificationHelper.showNotificationWithPendingIntent(title, body, pendingIntent, notificationType, dataId);
        
        // Update badge count for SESSION_READY notifications
        if ("SESSION_READY".equals(notificationType)) {
            try {
                com.example.therapyai.data.repository.ProcessedDataRepository.getInstance().refreshPendingData();
            } catch (Exception e) {
                Log.w(TAG, "Failed to refresh pending data after notification: " + e.getMessage());
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);

        if (SessionManager.getInstance().isSessionValid()) {
            NotificationRepository.getInstance().updateDeviceToken(token);
        }
    }


}