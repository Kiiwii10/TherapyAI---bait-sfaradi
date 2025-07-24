package com.example.therapyai.data.local;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.therapyai.data.repository.NotificationRepository;
import com.example.therapyai.ui.welcome.WelcomeActivity;
import com.example.therapyai.util.AppStateTracker;
import com.example.therapyai.util.HIPAAKeyManager;

/**
 * SessionManager handles user session state, timeouts, and forced logout.
 *
 * - Inactivity Timeout: 14 minutes -> show warning
 * - Absolute Timeout: 15 minutes -> force logout
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private Runnable delayedLogoutRunnable; // Add this line


    // Time thresholds
    private static final long INACTIVITY_TIMEOUT = 14 * 60 * 1000; // 14 minutes
    private static final long ABSOLUTE_TIMEOUT   = 15 * 60 * 1000; // 15 minutes

    private static SessionManager instance;
    private long appStartTime = System.currentTimeMillis();

    private final Context appContext;
    private final Handler handler;
    private Runnable inactivityRunnable;

    private long lastActivityTime;
    private long backgroundTime;
    private boolean isInactivityTimerPaused = false;

    private ReAuthListener reauthListener;
    private SessionTimeoutListener timeoutListener;

    public enum UserType { PATIENT, THERAPIST }

    /**
     * For the Activity/Fragment to receive a "time to warn user" event.
     */
    public interface SessionTimeoutListener {
        void onSessionTimeoutWarning(); // show a warning dialog or some UI
    }

    public interface ReAuthListener {
        void onReAuthRequested();
    }

    public void setReAuthListener(ReAuthListener listener) {
        this.reauthListener = listener;
    }

    public void requestReAuthentication() {
        if (reauthListener != null) {
            reauthListener.onReAuthRequested();
        }
    }


    private SessionManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        // Initialize lastActivityTime to current time to prevent immediate timeouts
        this.lastActivityTime = System.currentTimeMillis();
        Log.d(TAG, "DIAGNOSTIC: SessionManager initialized with lastActivityTime: " + lastActivityTime);
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager not initialized!");
        }
        return instance;
    }

    /**
     * Called whenever the user interacts with the app (key press, touch, etc.).
     * Resets the inactivity timer.
     */
    public void updateLastActivityTime() {
        long previousTime = lastActivityTime;
        lastActivityTime = System.currentTimeMillis();
        Log.d(TAG, "DIAGNOSTIC: updateLastActivityTime() - Previous: " + previousTime + ", New: " + lastActivityTime);
        
        cancelDelayedLogout(); // Cancel pending delayed logout
        if (!isInactivityTimerPaused) {
            resetInactivityTimer();
            Log.d(TAG, "DIAGNOSTIC: Timer not paused - reset inactivity timer");
        } else {
            Log.d(TAG, "DIAGNOSTIC: Timer paused - updated timestamp but not resetting callbacks");
        }
    }

    /**
     * Check if the session is valid:
     * - 1) The user data is still present in EphemeralPrefs
     * - 2) The user hasn't exceeded the absolute timeout (15min)
     */
    public boolean isSessionValid() {
        Log.d(TAG, "DIAGNOSTIC: isSessionValid() called");
        
        if (getUserType() == null || getUserId() == null) {
            Log.d(TAG, "DIAGNOSTIC: Session invalid - missing user data");
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long inactiveDuration = currentTime - lastActivityTime;
        boolean isValid = (inactiveDuration <= ABSOLUTE_TIMEOUT);
        
        Log.d(TAG, "DIAGNOSTIC: Session validation - Current: " + currentTime +
               ", Last activity: " + lastActivityTime +
               ", Inactive duration: " + inactiveDuration + "ms" +
               ", Absolute timeout: " + ABSOLUTE_TIMEOUT + "ms" +
               ", Timer paused: " + isInactivityTimerPaused +
               ", Valid: " + isValid);
               
        return isValid;
    }

    /**
     * Inactivity Timer:
     * After 14 minutes of inactivity, we post a warning via the listener.
     * We then schedule a forced logout in 1 minute if user does nothing.
     */
    private void resetInactivityTimer() {
        if (isInactivityTimerPaused) {
            Log.v(TAG, "resetInactivityTimer: Timer paused, skipping reset.");
            return;
        }

        handler.removeCallbacks(inactivityRunnable);
        inactivityRunnable = () -> {
            if (isInactivityTimerPaused) {
                Log.d(TAG, "Inactivity runnable fired, but timer is now paused. Ignoring.");
                return;
            }
            long inactiveDuration = System.currentTimeMillis() - lastActivityTime;
            if (inactiveDuration >= INACTIVITY_TIMEOUT) {
                Log.d(TAG, "Inactivity threshold reached. Warning user...");
                notifyTimeoutWarning();
            } else {
                Log.w(TAG, "Inactivity runnable fired early? Rescheduling.");
                resetInactivityTimer();
            }
        };
        // Post the check 14 minutes from now
        handler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT);
    }

    /**
     * Called when user has been idle for 14 minutes.
     * We warn them, and schedule a forced logout in 1 minute.
     */
    private void notifyTimeoutWarning() {
        if (isInactivityTimerPaused) {
            Log.d(TAG, "notifyTimeoutWarning: Timer is paused. Aborting warning and delayed logout.");
            return;
        }
        if (timeoutListener != null) {
            timeoutListener.onSessionTimeoutWarning();
        }
        // Cancel any existing delayed logout
        cancelDelayedLogout();
        delayedLogoutRunnable = this::delayedLogout;
        handler.postDelayed(delayedLogoutRunnable, 65_000); // Updated line
    }

    public void cancelDelayedLogout() { // Add this method
        if (delayedLogoutRunnable != null) {
            handler.removeCallbacks(delayedLogoutRunnable);
            delayedLogoutRunnable = null;
        }
    }

    /**
     * Called from onPause() in the activity to record background time and
     * cancel the inactivity check while in background.
     */
    public void setBackgroundTime() {
        if (!AppStateTracker.getInstance().isInSession()) {
            backgroundTime = System.currentTimeMillis();
            Log.d(TAG, "App going to background, removing inactivity/logout callbacks.");
            if (inactivityRunnable != null) {
                handler.removeCallbacks(inactivityRunnable);
            }
            cancelDelayedLogout();
        } else {
            Log.d(TAG, "App moving to a session activity, not setting background time.");
        }
    }

    /**
     * Called from onResume() in the activity:
     * If the app was in background > 15 minutes => immediate logout
     * Otherwise, reset inactivity time (user has returned)
     */
    public void checkForegroundStatus() {
        if (!isInactivityTimerPaused) {
            Log.d(TAG, "App returned to foreground. Resetting activity time.");
            updateLastActivityTime(); // This will reschedule the inactivity timer
        } else {
            Log.d(TAG, "App returned to foreground, but inactivity timer is currently paused.");
        }
    }

    /**
     * Clears ephemeral data and navigates to the welcome screen.
     */
//    public void forceLogout() {
//        // Clean ephemeral data
//        EphemeralPrefs.getInstance().clearAll();
//        // Cancel any queued warnings
//        handler.removeCallbacks(inactivityRunnable);
//
//        // Navigate to login/welcome
//        Intent intent = new Intent(appContext, WelcomeActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        appContext.startActivity(intent);
//    }

    public void forceLogout() {
        Log.w(TAG, "Forcing logout NOW.");
        // 1. Delete Keystore Key *FIRST*
        try {
            Log.d(TAG, "Attempting to delete HIPAA Keystore key...");
            HIPAAKeyManager.deleteKey(); // Call the new delete method
            Log.i(TAG, "HIPAA Keystore key deleted successfully.");
        } catch (Exception e) {
            // Log error but continue logout process. Key might not exist or other issue.
            Log.e(TAG, "Error deleting HIPAA Keystore key during logout", e);
        }

        // 2. Clean ephemeral data
        EphemeralPrefs.getInstance().clearAll();

        // 3. Cancel any queued warnings or logouts
        if (inactivityRunnable != null) {
            handler.removeCallbacks(inactivityRunnable);
            inactivityRunnable = null;
        }
        cancelDelayedLogout(); // Make sure delayed logout is cancelled

        // 4. Reset state flags
        isInactivityTimerPaused = false; // Reset pause state on logout

        // 5. Navigate to login/welcome
        Log.d(TAG, "Navigating to WelcomeActivity.");
        Intent intent = new Intent(appContext, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
    }


    /**
     * Public method to manually logout user (e.g. from a logout button).
     */
    public void logout() {
        Log.i(TAG, "User initiated logout.");
        NotificationRepository.getInstance().unregisterDevice();
        forceLogout();
    }

    /**
     * For the activity to listen for inactivity warnings.
     */
    public void setSessionTimeoutListener(SessionTimeoutListener listener) {
        this.timeoutListener = listener;
    }

    // -------------------------
    //  Ephemeral-based user data
    // -------------------------
    public void setUserType(UserType userType) {
        if (userType == null) {
            EphemeralPrefs.getInstance().storeUserType(null);
        } else {
            EphemeralPrefs.getInstance().storeUserType(userType.name());
        }
    }

    public UserType getUserType() {
        // DIAGNOSTIC: Log the context of getUserType call
        Log.d(TAG, "DIAGNOSTIC: getUserType() called");
        Log.d(TAG, "DIAGNOSTIC: Inactivity timer paused: " + isInactivityTimerPaused);
        Log.d(TAG, "DIAGNOSTIC: Last activity time: " + lastActivityTime);
        Log.d(TAG, "DIAGNOSTIC: Current time: " + System.currentTimeMillis());
        Log.d(TAG, "DIAGNOSTIC: Time since last activity: " + (System.currentTimeMillis() - lastActivityTime) + "ms");
        
        String stored = EphemeralPrefs.getInstance().getUserType();
        if (stored == null) {
            Log.w(TAG, "DIAGNOSTIC: getUserType() returned null - no stored user type found");
            return null;
        }
        try {
            UserType result = UserType.valueOf(stored.toUpperCase());
            Log.d(TAG, "DIAGNOSTIC: getUserType() successful: " + result);
            return result;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "DIAGNOSTIC: getUserType() failed to parse stored value: " + stored, e);
            return null;
        }
    }

    /**
     * Pauses the inactivity timer checks (warning and forced logout based on inactivity).
     * The absolute background timeout (15 mins) still applies.
     */
    public void pauseInactivityTimer() {
        Log.d(TAG, "DIAGNOSTIC: Pausing inactivity timer.");
        Log.d(TAG, "DIAGNOSTIC: Timer already paused: " + isInactivityTimerPaused);
        Log.d(TAG, "DIAGNOSTIC: Time since last activity: " + (System.currentTimeMillis() - lastActivityTime) + "ms");
        Log.d(TAG, "DIAGNOSTIC: ABSOLUTE_TIMEOUT: " + ABSOLUTE_TIMEOUT + "ms");
        
        isInactivityTimerPaused = true;
        
        // IMPORTANT: Refresh the last activity time when pausing to prevent session timeout
        // This ensures that time spent in BaseSessionActivity doesn't count against session validity
        updateLastActivityTime();
        
        // Remove any pending inactivity checks or delayed logouts
        if (inactivityRunnable != null) {
            handler.removeCallbacks(inactivityRunnable);
            Log.d(TAG, "DIAGNOSTIC: Removed pending inactivity runnable");
        }
        cancelDelayedLogout(); // Ensure delayed logout is also cancelled
        Log.d(TAG, "DIAGNOSTIC: Inactivity timer paused successfully");
    }

    /**
     * Resumes the inactivity timer checks. Resets the last activity time.
     */
    public void resumeInactivityTimer() {
        if (!isInactivityTimerPaused) {
            Log.d(TAG, "DIAGNOSTIC: resumeInactivityTimer called but timer was not paused");
            return; // Only resume if it was paused
        }
        Log.d(TAG, "DIAGNOSTIC: Resuming inactivity timer.");
        Log.d(TAG, "DIAGNOSTIC: Time since last activity before resume: " + (System.currentTimeMillis() - lastActivityTime) + "ms");
        
        isInactivityTimerPaused = false;
        updateLastActivityTime(); // Restart the timer logic immediately
        
        Log.d(TAG, "DIAGNOSTIC: Time since last activity after updateLastActivityTime: " + (System.currentTimeMillis() - lastActivityTime) + "ms");
        Log.d(TAG, "DIAGNOSTIC: Inactivity timer resumed successfully");
    }

    /**
     * Get the app start time for crash recovery detection
     */
    public long getAppStartTime() {
        return appStartTime;
    }

    public void setUserId(String userId) {
        EphemeralPrefs.getInstance().storeUserId(userId);
    }

    public String getUserId() {
        return EphemeralPrefs.getInstance().getUserId();
    }
    public String getUserFullName() {
        return EphemeralPrefs.getInstance().getUserFullName();
    }

    public String getTherapistDetails() {
        String userId = getUserId();
        String userFullName = getUserFullName();
        String email = getUserEmail();
        return "{" +
                "id: " + userId + ", " +
                "name: " + userFullName + ", " +
                "email: " + email + "}";
    }

    public void delayedLogout(){
        if (System.currentTimeMillis() - lastActivityTime >= INACTIVITY_TIMEOUT) {
            forceLogout();
        }
    }

    public void setLoginUser(String email,
                             String id,
                             String userFullName,
                             String dateOfBirth,
                             String password,
                             UserType userType) {
        EphemeralPrefs ephemeralPrefs = EphemeralPrefs.getInstance();
        ephemeralPrefs.storeUserEmail(email);
        ephemeralPrefs.storeUserId(id);
        ephemeralPrefs.storeUserFullName(userFullName);
        ephemeralPrefs.storeUserPassword(password);
        ephemeralPrefs.storeUserType(userType.name());
        ephemeralPrefs.storeUserDateOfBirth(dateOfBirth);

        isInactivityTimerPaused = false;
        updateLastActivityTime();
        setBackgroundTime();

        NotificationRepository.getInstance().registerDevice();
    }

    public String getUserEmail() {
        return EphemeralPrefs.getInstance().getUserEmail();
    }
    public String getUserDateOfBirth() {
        return EphemeralPrefs.getInstance().getUserDateOfBirth();
    }

    /**
     * Get the last activity time for diagnostic purposes
     */
    public long getLastActivityTime() {
        return lastActivityTime;
    }
}
