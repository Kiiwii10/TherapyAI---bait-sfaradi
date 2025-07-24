package com.example.therapyai.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.remote.TherapyApiImpl;
import com.example.therapyai.data.remote.models.DeviceRegistrationRequest;
import com.google.firebase.messaging.FirebaseMessaging;



public class NotificationRepository {
    private static final String TAG = "NotificationRepository";
    private static NotificationRepository instance;
    private final TherapyApiImpl apiImpl;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1000;

    private NotificationRepository(TherapyApiImpl apiImpl) {
        this.apiImpl = apiImpl;
    }

    public static synchronized void init(boolean useMockData) {
        if (instance == null) {
            instance = new NotificationRepository(TherapyApiImpl.getInstance(useMockData));
        }
    }

    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NotificationRepository not initialized!");
        }
        return instance;
    }

    public void registerDevice() {
        if (SessionManager.getInstance().getUserType() == null) {
            Log.d(TAG, "User not logged in, skipping device registration");
            return;
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token obtained: " + token);
                    sendRegistrationToServer(token, 0); // Start registration with attempt 0
                });
    }

    public void updateDeviceToken(String newToken) {
        if (SessionManager.getInstance().getUserType() == null) {
            Log.d(TAG, "User not logged in, skipping token update");
            return;
        }
        Log.d(TAG, "FCM Token refreshed. Updating on server.");
        sendRegistrationToServer(newToken, 0); // Start update with attempt 0
    }

    private void sendRegistrationToServer(final String fcmToken, final int attempt) {
        String authToken = EphemeralPrefs.getInstance().getSessionToken();
        if (authToken == null) {
            Log.w(TAG, "No auth token available, cannot register device token.");
            return;
        }
        String userId = SessionManager.getInstance().getUserId();
        if (userId == null || SessionManager.getInstance().getUserType() == null) {
            Log.w(TAG, "No user ID or type available, cannot register device token.");
            return;
        }
        DeviceRegistrationRequest request = new DeviceRegistrationRequest(
                fcmToken,
                "ANDROID",
                userId,
                SessionManager.getInstance().getUserType().name()
        );

        Log.d(TAG, "Attempting to register device token (Attempt " + (attempt + 1) + ")");
        apiImpl.registerDevice(authToken, request, new TherapyApiImpl.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.i(TAG, "Device token registered/updated successfully on server.");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to register device token (Attempt " + (attempt + 1) + "): " + error);
                if (attempt < MAX_RETRIES) {
                    // Schedule retry
                    retryHandler.postDelayed(() -> {
                        Log.i(TAG, "Retrying device token registration...");
                        sendRegistrationToServer(fcmToken, attempt + 1);
                    }, RETRY_DELAY_MS);
                } else {
                    Log.e(TAG, "Max retries reached for device token registration. Giving up.");
                }
            }
        });
    }

    public void unregisterDevice() {
        unregisterDeviceInternal(0); // Start unregistration with attempt 0
    }

    private void unregisterDeviceInternal(final int attempt) {
        String authToken = EphemeralPrefs.getInstance().getSessionToken();
        // Allow unregistration attempt without token, change later if needed
        // if (authToken == null) {
        //     Log.d(TAG, "No auth token, skipping device unregistration");
        //     return;
        // }

        String userId = SessionManager.getInstance().getUserId();
        if (userId == null) {
            Log.w(TAG, "No user ID available, cannot unregister device token.");
            return;
        }

        String bearerToken = (authToken != null) ? authToken : null;

        Log.d(TAG, "Attempting to unregister device token (Attempt " + (attempt + 1) + ")");
        apiImpl.unregisterDevice(bearerToken, userId, new TherapyApiImpl.ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.i(TAG, "Device token unregistered successfully on server.");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to unregister device token (Attempt " + (attempt + 1) + "): " + error);
                if (attempt < MAX_RETRIES) {
                    // Schedule retry
                    retryHandler.postDelayed(() -> {
                        Log.i(TAG, "Retrying device token unregistration...");
                        unregisterDeviceInternal(attempt + 1);
                    }, RETRY_DELAY_MS);
                } else {
                    Log.e(TAG, "Max retries reached for device token unregistration. Giving up.");
                }
            }
        });
    }
}