package com.example.therapyai;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import org.jetbrains.annotations.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.ui.welcome.WelcomeActivity;
import com.example.therapyai.util.HIPAAKeyManager;

public abstract class BaseSignedActivity extends AppCompatActivity
        implements SessionManager.SessionTimeoutListener,
        SessionManager.ReAuthListener  {

    private static final int REQUEST_CODE_REAUTH = 1001;
    private AlertDialog warningDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the activity to be secure - no screenshots or screen sharing
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );


        // If session is not valid, redirect immediately
        if (!SessionManager.getInstance().isSessionValid()) {
            Log.d("BaseSignedActivity", "Session is not valid, navigating to WelcomeActivity");
            navigateToWelcome();
            return;
        }

        // become the listener for session timeouts
        SessionManager.getInstance().setSessionTimeoutListener(this);

        // become the listener for re-authentication
        SessionManager.getInstance().setReAuthListener(this);
    }

    /**
     * Called when the user touches the screen or interacts in any way
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        SessionManager.getInstance().updateLastActivityTime();
    }

    /**
     * Also update last activity time on any key events
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        SessionManager.getInstance().updateLastActivityTime();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        SessionManager.getInstance().updateLastActivityTime();
        return super.dispatchTrackballEvent(event);
    }

    /**
     * When the user returns to this Activity from background
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!SessionManager.getInstance().isSessionValid()) {
            navigateToWelcome();
            return; // IMPORTANT: Stop execution if session is invalid.
        }
        SessionManager.getInstance().checkForegroundStatus();
        SessionManager.getInstance().setSessionTimeoutListener(this);
    }

    /**
     * When the Activity goes into background
     */
    @Override
    protected void onPause() {
        super.onPause();
        dismissWarningDialog();
        // Mark time we went to background
        SessionManager.getInstance().setBackgroundTime();
    }

    /**
     * Implementation of SessionTimeoutListener:
     * Called by SessionManager at 14 minutes idle -> show a warning
     */
    @Override
    public void onSessionTimeoutWarning() {
        showWarningDialog();
    }

    /**
     * Show an AlertDialog warning the user that they will be logged out in 1 minute
     */
    private void showWarningDialog() {
        if (warningDialog != null && warningDialog.isShowing()) {
            return;
        }

        warningDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.session_timeout_title)
                .setMessage(R.string.session_timeout_message)
                .setPositiveButton(R.string.stay_logged_in, (dialog, which) -> {
                    // User wants to stay -> reset the activity time
                    SessionManager.getInstance().updateLastActivityTime();
                })
                .setCancelable(false)
                .show();
    }    /**
     * This is called whenever a piece of code in the app
     * hits UserNotAuthenticatedException and calls requestReauthentication().
     * Show a device credential or BiometricPrompt without killing the Activity.
     * */
    @Override
    public void onReAuthRequested() {
        runOnUiThread(() -> {
            Log.d("BaseSignedActivity", "Re-authentication requested, showing auth prompt...");
            showReAuthPrompt();
        });
    }


    private void showReAuthPrompt() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isKeyguardSecure()) {
            Intent authIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                    getString(R.string.auth_dialog_title),
                    getString(R.string.auth_dialog_description)
            );
            
            if (authIntent != null) {
                try {
                    startActivityForResult(authIntent, REQUEST_CODE_REAUTH);
                } catch (Exception e) {
                    Log.e("BaseSignedActivity", "Failed to show re-auth dialog", e);
                    Toast.makeText(this, "Authentication required. Please restart the app.", Toast.LENGTH_LONG).show();
                    SessionManager.getInstance().forceLogout();
                }
            } else {
                Log.e("BaseSignedActivity", "Failed to create auth intent - null intent returned");
                Toast.makeText(this, "Authentication required. Please restart the app.", Toast.LENGTH_LONG).show();
                SessionManager.getInstance().forceLogout();
            }
        } else {
            Log.e("BaseSignedActivity", "Device doesn't have secure lock screen or keyguard not secure");
            Toast.makeText(this, "Secure lock screen required for this app", Toast.LENGTH_LONG).show();
            SessionManager.getInstance().forceLogout();
        }
    }    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_REAUTH) {
            if (resultCode == RESULT_OK) {
                // User re-authenticated successfully
                Log.d("BaseSignedActivity", "User re-authenticated successfully. Key should be unlocked now.");
                
                // Reset the HIPAA key - this will recreate it with the user already authenticated
                try {
                    HIPAAKeyManager.deleteKey();
                    // Force get the key to ensure it's created correctly while the user is authenticated
                    HIPAAKeyManager.getOrCreateKey();
                    Log.d("BaseSignedActivity", "HIPAA key refreshed successfully after re-auth");
                } catch (Exception e) {
                    Log.e("BaseSignedActivity", "Error refreshing HIPAA key after re-auth", e);
                }
                
                // Notify the user
                Toast.makeText(this, "Re-authenticated successfully", Toast.LENGTH_SHORT).show();
                
                // Resume normal activity
                updateLastActivityTime();
            } else {
                // User canceled or failed
                Log.w("BaseSignedActivity", "Re-authentication failed or canceled by user");
                Toast.makeText(this, "Authentication failed. Logging out for security.", Toast.LENGTH_LONG).show();
                SessionManager.getInstance().forceLogout();
            }
        }
    }

    /**
     * Updates the last activity time in SessionManager
     */
    protected void updateLastActivityTime() {
        SessionManager.getInstance().updateLastActivityTime();
    }


    /**
     * Dismiss the warning dialog, if any
     */
    private void dismissWarningDialog() {
        if (warningDialog != null && warningDialog.isShowing()) {
            warningDialog.dismiss();
        }
        warningDialog = null;
    }

    /**
     * If the session is invalid, we jump to the welcome screen
     */
    private void navigateToWelcome() {
        Intent i = new Intent(this, WelcomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
