package com.example.therapyai.ui.welcome;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.therapyai.R;
import com.example.therapyai.data.repository.AuthRepository;
import com.example.therapyai.ui.MainActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;
import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_DEVICE_CREDENTIAL = 555; // For fallback using KeyguardManager

    private TextInputEditText emailEditText, passwordEditText;
    private MaterialButton loginButton;
    private TextView forgotPasswordTextView;
    private ProgressBar loginProgressBar;

    private AuthRepository authRepository;
    private String pendingEmail = null;
    private String pendingPassword = null;

    // BiometricPrompt objects
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean isAuthenticating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Toolbar toolbar = findViewById(R.id.childToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Login");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        authRepository = AuthRepository.getInstance();

        emailEditText = findViewById(R.id.loginEmailEditText);
        passwordEditText = findViewById(R.id.loginPasswordEditText);
        loginButton = findViewById(R.id.loginButton);
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        loginProgressBar = findViewById(R.id.loginProgressBar);

        loginButton.setOnClickListener(v -> {
            hideKeyboard();
            String email = Objects.requireNonNull(emailEditText.getText()).toString().trim();
            String password = Objects.requireNonNull(passwordEditText.getText()).toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in both email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            showLoginTermsDialog(email, password);
        });

        forgotPasswordTextView.setOnClickListener(v -> showForgotPasswordDialog());
        setupBiometricPrompt();
    }

    /**
     * Displays Terms & Conditions in a dialog.
     * On "Login", we proceed to a user-auth (biometric or device credential) step.
     */
    private void showLoginTermsDialog(String email, String password) {
        SpannableString termsText = new SpannableString("By logging in, you agree to our Terms and Conditions.");
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openTermsAndConditions();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(ContextCompat.getColor(LoginActivity.this, R.color.m3_inverse_primary_light)); // link color
                ds.setUnderlineText(true);
            }
        };

        // Make "Terms and Conditions" clickable
        termsText.setSpan(clickableSpan, 32, 52, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Put it in a TextView for the dialog
        TextView textView = new TextView(this);
        textView.setText(termsText);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(50, 20, 50, 20);
        textView.setTextSize(16);

        new AlertDialog.Builder(this)
                .setTitle("Terms and Conditions")
                .setView(textView)
                .setPositiveButton("Login", (dialog, which) -> {
                    // User accepts T&C => now authenticate
                    authenticateBeforeLogin(email, password);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    /**
     * The core method that either shows the BiometricPrompt or if not possible,
     * attempts a fallback to device credentials via KeyguardManager.
     */
    private void authenticateBeforeLogin(String email, String password) {
        pendingEmail = email;
        pendingPassword = password;

        loginProgressBar.setVisibility(View.VISIBLE);

        BiometricManager biometricManager = BiometricManager.from(this);
        int authResult = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );

        switch (authResult) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                isAuthenticating = true;
                biometricPrompt.authenticate(promptInfo);
                break;

            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
            default:
                fallbackToDeviceCredentialPrompt();
                break;
        }
    }

    /**
     * Build the BiometricPrompt with correct settings for different API levels.
     */
    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.d("LoginActivity", "Biometric Auth Error. Code: " + errorCode + " Msg: " + errString + " isFinishing: " + isFinishing());
                isAuthenticating = false; // Reset flag

                if (!isFinishing() && !isDestroyed()) {
                    loginProgressBar.setVisibility(View.GONE); // Only hide if not finishing
                    Toast.makeText(LoginActivity.this, "Auth error: " + errString, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Log.d("LoginActivity", "Biometric Auth Succeeded. isFinishing: " + isFinishing());
                isAuthenticating = false; // Reset flag
                if (pendingEmail != null && pendingPassword != null) {
                    logInUser(pendingEmail, pendingPassword);
                    pendingEmail = null;
                    pendingPassword = null;
                } else {
                    if (!isFinishing() && !isDestroyed()) {
                        loginProgressBar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.d("LoginActivity", "Biometric Auth Failed. isFinishing: " + isFinishing());
                isAuthenticating = false; // Reset flag
                if (!isFinishing() && !isDestroyed()) {
                    loginProgressBar.setVisibility(View.GONE); // Only hide if not finishing
                    Toast.makeText(LoginActivity.this, "Biometric authentication failed.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Build the PromptInfo with fallback handling
        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authenticate Before Logging In")
                .setSubtitle("Use your biometric or device credential");


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL
            );
        } else {
            promptBuilder.setDeviceCredentialAllowed(true);
        }


        promptInfo = promptBuilder.build();
    }

    /**
     * Fallback approach for devices with no biometrics or hardware
     * or if we can't use BiometricPrompt. We'll manually show the system
     * "Confirm device credentials" prompt.
     */
    private void fallbackToDeviceCredentialPrompt() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager == null) {
            Toast.makeText(this, "No KeyguardManager available!", Toast.LENGTH_SHORT).show();
            loginProgressBar.setVisibility(View.GONE);
            return;
        }

        if (!keyguardManager.isKeyguardSecure()) {
            // No lock screen set up at all
            Toast.makeText(this, "Please set up a device lock screen (PIN, pattern, or biometric).", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            loginProgressBar.setVisibility(View.GONE);
            return;
        }

        // Show the standard lockscreen prompt
        Intent authIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Verify your identity",
                "Unlock to log in"
        );
        try {
            startActivityForResult(authIntent, REQUEST_CODE_DEVICE_CREDENTIAL);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to show credential prompt: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            loginProgressBar.setVisibility(View.GONE);
        }
    }

    /**
     * Called when user completes or cancels the fallback device-credential prompt.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) {
                if (pendingEmail != null && pendingPassword != null) {
                    logInUser(pendingEmail, pendingPassword);
                }
            } else {
                loginProgressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Device credential prompt canceled.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Actually calls the server to log in.
     * Once successful, store tokens in EphemeralPrefs, etc.
     */
    private void logInUser(String email, String password) {
        authRepository.login(email, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(boolean success) {
                runOnUiThread(() -> {
                    loginProgressBar.setVisibility(View.GONE);
                    navigateToMainActivity();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loginProgressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "Login error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Example "Forgot password" stub
     */
    private void showForgotPasswordDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Forgot Password")
                .setMessage("Feature un-supported yet. Spanish House developers, please tailor this feature to " +
                        "the spanish house account handling policies.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Open Terms & Conditions screen (e.g., full screen fragment or activity)
     */
    private void openTermsAndConditions() {
        FullScreenTermsDialogFragment dialog = new FullScreenTermsDialogFragment();
        dialog.show(getSupportFragmentManager(), "TermsDialog");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // "Up" arrow tapped => go to WelcomeActivity
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finishAfterTransition();
            overridePendingTransition(0, 0);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAfterTransition();
        overridePendingTransition(0, 0);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isAuthenticating && biometricPrompt != null) {
            biometricPrompt.cancelAuthentication(); // Will trigger onAuthenticationError
            isAuthenticating = false; // Reset flag here too
        }
    }

    @Override
    protected void onDestroy() { // Good practice to dismiss dialogs
        super.onDestroy();
        // If you made termsDialog and forgotPasswordDialog class members:
        // if (termsDialog != null && termsDialog.isShowing()) {
        //     termsDialog.dismiss();
        // }
        // if (forgotPasswordDialog != null && forgotPasswordDialog.isShowing()) {
        //     forgotPasswordDialog.dismiss();
        // }
    }
}
