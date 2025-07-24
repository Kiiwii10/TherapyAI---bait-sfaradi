package com.example.therapyai.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;
import android.util.Log;

import com.example.therapyai.util.AESUtil;
import com.example.therapyai.util.HIPAAKeyManager;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;

public class EphemeralPrefs {
    private static final String TAG = "EphemeralPrefs";
    private static final String PREF_NAME = "ephemeral_prefs";

    // Keys for tokens
    private static final String KEY_SESSION_TOKEN = "encrypted_session_token";
    private static final String KEY_REFRESH_TOKEN = "encrypted_refresh_token";

    // Keys for user data
    private static final String KEY_USER_TYPE = "encrypted_user_type";
    private static final String KEY_USER_ID   = "encrypted_user_id";
    private static final String KEY_USER_NAME = "encrypted_user_name";
//    private static final String KEY_DEVICE_ID   = "encrypted_device_id";
    private static final String KEY_USER_EMAIL = "encrypted_user_email";
    private static final String KEY_USER_DATE_BIRTH = "encrypted_user_date_birth";
    private static final String KEY_USER_PASSWORD = "encrypted_user_password";

    private static EphemeralPrefs instance;
    private final SharedPreferences sharedPreferences;

    private EphemeralPrefs(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new EphemeralPrefs(context);
        }
    }

    public static synchronized EphemeralPrefs getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EphemeralPrefs not initialized!");
        }
        return instance;
    }


    public void putEncrypted(String key, String plainValue) {
        if (plainValue == null) {
            sharedPreferences.edit().remove(key).apply();
            return;
        }

        try {
            SecretKey ephemeralKey = getMasterKey();
            if (ephemeralKey == null) {
                Log.e(TAG, "No master key available. Cannot encrypt. Requesting re-auth.");
                // If key isn't even available, re-auth might help re-create/unlock it.
                SessionManager.getInstance().requestReAuthentication();
                return;
            }
            byte[] encrypted = AESUtil.encryptAesGcm(plainValue.getBytes("UTF-8"), ephemeralKey);
            String base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            sharedPreferences.edit().putString(key, base64).apply();
        } catch (UserNotAuthenticatedException unae) {
            Log.e(TAG, "Encryption failed: User not authenticated for Keystore key. Requesting re-auth.", unae);
            SessionManager.getInstance().requestReAuthentication();
        } catch (AEADBadTagException ae) { // Catch specific crypto exception that often indicates auth issues with GCM
            Log.e(TAG, "Encryption failed: AEADBadTagException, possibly due to Keystore auth issue. Requesting re-auth.", ae);
            SessionManager.getInstance().requestReAuthentication();
        }
        catch (Exception e) { // Catch other crypto or general exceptions
            Log.e(TAG, "Encryption failed with general exception: " + e.getMessage() + ". Forcing logout.", e);
            SessionManager.getInstance().forceLogout(); // For other unexpected errors, logout might be safer
        }
    }

    public String getDecrypted(String key) {
        String base64 = sharedPreferences.getString(key, null);
        if (base64 == null) return null;

        // DIAGNOSTIC: Log the decryption attempt context
        Log.d(TAG, "DIAGNOSTIC: Attempting to decrypt key: " + key);
        Log.d(TAG, "DIAGNOSTIC: Current thread: " + Thread.currentThread().getName());
        Log.d(TAG, "DIAGNOSTIC: App process PID: " + android.os.Process.myPid());
        
        try {
            SecretKey ephemeralKey = getMasterKey();
            if (ephemeralKey == null) {
                Log.e(TAG, "DIAGNOSTIC: No master key found for decryption. Requesting re-auth.");
                SessionManager.getInstance().requestReAuthentication();
                return null;
            }
            
            // DIAGNOSTIC: Log key state before decryption
            Log.d(TAG, "DIAGNOSTIC: Master key obtained successfully. Key algorithm: " + ephemeralKey.getAlgorithm());
            
            byte[] cipherData = Base64.decode(base64, Base64.NO_WRAP);
            Log.d(TAG, "DIAGNOSTIC: Cipher data length: " + cipherData.length);
            
            byte[] decrypted = AESUtil.decryptAesGcm(cipherData, ephemeralKey);
            Log.d(TAG, "DIAGNOSTIC: Decryption successful for key: " + key);
            return new String(decrypted, "UTF-8");
        } catch (UserNotAuthenticatedException unae) {
            Log.e(TAG, "DIAGNOSTIC: UserNotAuthenticatedException - Keystore key not authenticated for key: " + key, unae);
            Log.e(TAG, "DIAGNOSTIC: This usually means the 12-hour authentication period expired");
            Log.e(TAG, "DIAGNOSTIC: Session manager inactivity timer paused: " +
                (SessionManager.getInstance() != null ? "unknown" : "SessionManager not available"));
            try {
                Log.e(TAG, "DIAGNOSTIC: Time since last activity: " +
                    (System.currentTimeMillis() - SessionManager.getInstance().getLastActivityTime()) + "ms");
            } catch (Exception e) {
                Log.e(TAG, "DIAGNOSTIC: Could not get last activity time", e);
            }
            SessionManager.getInstance().requestReAuthentication();
            return null;
        } catch (AEADBadTagException ae) { // Catch specific crypto exception
            Log.e(TAG, "DIAGNOSTIC: AEADBadTagException for key: " + key + " - GCM tag verification failed.", ae);
            Log.e(TAG, "DIAGNOSTIC: This typically indicates:");
            Log.e(TAG, "DIAGNOSTIC: 1. Key authentication expired during long session");
            Log.e(TAG, "DIAGNOSTIC: 2. Key was recreated/invalidated while encrypted data still exists");
            Log.e(TAG, "DIAGNOSTIC: 3. Data corruption or wrong key being used");
            try {
                Log.e(TAG, "DIAGNOSTIC: Time since last activity: " +
                    (System.currentTimeMillis() - SessionManager.getInstance().getLastActivityTime()) + "ms");
                Log.e(TAG, "DIAGNOSTIC: Session timeout thresholds - Inactivity: " + (14 * 60 * 1000) + "ms, Absolute: " + (15 * 60 * 1000) + "ms");
            } catch (Exception e) {
                Log.e(TAG, "DIAGNOSTIC: Could not get timing info", e);
            }
            
            // Handle the case where the key was recreated after app crash but encrypted data still exists
            if (shouldClearDataAndForceLogout(key)) {
                Log.w(TAG, "Key mismatch detected (likely after app crash). Clearing all encrypted data and forcing logout.");
                clearAll(); // Clear all encrypted data since it's now unreadable
                SessionManager.getInstance().forceLogout();
            } else {
                SessionManager.getInstance().requestReAuthentication();
            }
            return null;
        }
        catch (Exception e) { // Catch other crypto or general exceptions
            Log.e(TAG, "DIAGNOSTIC: General decryption exception: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            SessionManager.getInstance().forceLogout(); // For other unexpected errors, logout might be safer
            return null;
        }
    }
    /**
     * Determine if we should attempt session recovery based on the key being decrypted
     * and whether we're currently in a session or just restarted from a crash
     */
    private boolean shouldAttemptSessionRecovery(String key) {
        try {
            // Check if we're currently in a session
            boolean currentlyInSession = com.example.therapyai.util.AppStateTracker.getInstance().isInSession();
            
            // Check if this is a user credential key (these should always force logout if failing)
            boolean isUserCredentialKey = key.equals(KEY_USER_TYPE) || key.equals(KEY_USER_EMAIL) || key.equals(KEY_USER_PASSWORD);
            
            // Only attempt recovery if:
            // 1. We're currently in a session, OR
            // 2. This is not a critical user credential key
            // 3. The app recently started (potential crash recovery)
            boolean recentAppStart = (System.currentTimeMillis() - getAppStartTime()) < 30000; // 30 seconds
            
            boolean shouldRecover = currentlyInSession || (!isUserCredentialKey && recentAppStart);
            
            Log.d(TAG, "Session recovery decision: currentlyInSession=" + currentlyInSession +
                  ", isUserCredentialKey=" + isUserCredentialKey +
                  ", recentAppStart=" + recentAppStart +
                  ", shouldRecover=" + shouldRecover);
                  
            return shouldRecover;
        } catch (Exception e) {
            Log.e(TAG, "Error determining session recovery", e);
            return false;
        }
    }
    
    /**
     * Determine if we should clear all data and force logout vs request re-authentication
     */
    private boolean shouldClearDataAndForceLogout(String key) {
        try {
            // If this is a critical user credential key and we're getting decryption failures,
            // it's likely the key was recreated after an app crash
            boolean isUserCredentialKey = key.equals(KEY_USER_TYPE) || key.equals(KEY_USER_EMAIL) || key.equals(KEY_USER_ID);
            
            // Check if the app recently started (potential crash recovery)
            boolean recentAppStart = (System.currentTimeMillis() - getAppStartTime()) < 60000; // 60 seconds
            
            // If we have user credential failures soon after app start, it's likely a key mismatch from crash
            boolean shouldClearData = isUserCredentialKey && recentAppStart;
            
            Log.d(TAG, "shouldClearDataAndForceLogout: key=" + key +
                  ", isUserCredentialKey=" + isUserCredentialKey +
                  ", recentAppStart=" + recentAppStart +
                  ", shouldClearData=" + shouldClearData);
                  
            return shouldClearData;
        } catch (Exception e) {
            Log.e(TAG, "Error determining clear data decision", e);
            return false; // Default to re-auth on error
        }
    }
    
    /**
     * Get app start time for crash recovery detection
     */
    private long getAppStartTime() {
        try {
            // Use a simple approach - check when the Application class was initialized
            return SessionManager.getInstance().getAppStartTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Master Key from Keystore
     */
    private static SecretKey getMasterKey() throws Exception {
        return HIPAAKeyManager.getOrCreateKey();
    }

    public String[] getUserFromMemory() {
        String password = getDecrypted(KEY_USER_PASSWORD);
        String email = getDecrypted(KEY_USER_EMAIL);
        if (password == null || email == null) {
            return null;
        }
        return new String[]{email, password};
    }

    // Token helpers
    public void storeSessionToken(String token) {
        putEncrypted(KEY_SESSION_TOKEN, token);
    }

    public String getSessionToken() {
        return getDecrypted(KEY_SESSION_TOKEN);
    }

    public void storeRefreshToken(String token) {
        putEncrypted(KEY_REFRESH_TOKEN, token);
    }

    public String getRefreshToken() {
        return getDecrypted(KEY_REFRESH_TOKEN);
    }

    // User data helpers
    public void storeUserType(String userType) {
        putEncrypted(KEY_USER_TYPE, userType);
    }

    public String getUserType() {
        return getDecrypted(KEY_USER_TYPE);
    }

    public void storeUserId(String userId) {
        putEncrypted(KEY_USER_ID, userId);
    }

    public String getUserId() {
        return getDecrypted(KEY_USER_ID);
    }
    public void storeUserFullName(String userName) {
        putEncrypted(KEY_USER_NAME, userName);
    }
    public String getUserFullName() {
        return getDecrypted(KEY_USER_NAME);
    }

    public void storeUserEmail(String email) {
        putEncrypted(KEY_USER_EMAIL, email);
    }

    public String getUserEmail() {
        return getDecrypted(KEY_USER_EMAIL);
    }

    public void storeUserPassword(String password) {
        putEncrypted(KEY_USER_PASSWORD, password);
    }

    public String getUserPassword() {
        return getDecrypted(KEY_USER_PASSWORD);
    }

    public void storeUserDateOfBirth(String dateOfBirth) {
        putEncrypted(KEY_USER_DATE_BIRTH, dateOfBirth);
    }

    public String getUserDateOfBirth() {
        return getDecrypted(KEY_USER_DATE_BIRTH);
    }



    /**
     * Clear everything stored
     */
    public void clearAll() {
        sharedPreferences.edit().clear().apply();
    }
}
