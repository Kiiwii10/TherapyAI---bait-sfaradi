package com.example.therapyai.data.repository;


import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.SessionManager;
import com.example.therapyai.data.remote.TherapyApiImpl;
import com.example.therapyai.data.remote.models.LoginResponse;
import com.example.therapyai.data.remote.models.PasswordResetResponse;

/**
 * A repository that orchestrates authentication-related API calls
 * and optionally handles storing tokens, etc.
 */
public class AuthRepository {

    private static AuthRepository instance;
    private final TherapyApiImpl apiImpl;

    private AuthRepository(TherapyApiImpl apiImpl) {
        this.apiImpl = apiImpl;
    }

    /**
     * Get a singleton instance of AuthRepository.
     * @param useMockData whether to use mock data or real network calls
     */
    public static synchronized void init(boolean useMockData) {
        if (instance == null) {
            instance = new AuthRepository(TherapyApiImpl.getInstance(useMockData));
        }
    }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AuthRepository not initialized!");
        }
        return instance;
    }

    // --------------------------------------------
    // Callback interfaces for the UI layer
    // --------------------------------------------
    public interface LoginCallback {
        void onSuccess(boolean success);
        void onError(String error);
    }

    public interface PasswordResetCallback {
        void onSuccess(PasswordResetResponse response);
        void onError(String error);
    }

    // --------------------------------------------
    // PUBLIC METHODS (the API to the rest of the app)
    // --------------------------------------------

    /**
     * Logs in a user with username/password.
     */
    public void login(String email, String password, LoginCallback callback) {
        // Maybe do validation here or token checks
        apiImpl.loginUser(email, password, new TherapyApiImpl.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                if (result == null || result.getToken() == null || result.getRefreshToken() == null) {
                    callback.onSuccess(false);
                    return;
                }

                EphemeralPrefs.getInstance().storeSessionToken(result.getToken());
                EphemeralPrefs.getInstance().storeRefreshToken(result.getRefreshToken());
                result.setToken(null); // clear token from memory
                result.setRefreshToken(null); // clear refresh token from memory
                loginToSessionManager(result, email, password); // TODO: maybe remove password storage, all it does is to handle re-login
                callback.onSuccess(true);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void loginToSessionManager(LoginResponse result,
                                       String email,
                                       String password) {
        String userType = result.getUserType().toLowerCase();
        SessionManager.UserType sessionUserType = null;
        switch (userType) {
            case "patient":
                sessionUserType = SessionManager.UserType.PATIENT;
                break;
            case "therapist":
                sessionUserType = SessionManager.UserType.THERAPIST;
                break;
            default:
                return; // invalid user type, don't log in
        }
        SessionManager.getInstance().setLoginUser(
                email,
                result.getUserId(),
                result.getUserFullName(),
                result.getDateOfBirth(),
                password,
                sessionUserType
        );
    }

    public void loginFromMemory(LoginCallback callback) {
        String[] cred = EphemeralPrefs.getInstance().getUserFromMemory();
        if (cred == null) {
            callback.onError("No user found in memory");
            return;
        }
        login(cred[0], cred[1], new LoginCallback() {
            @Override
            public void onSuccess(boolean success) {
                callback.onSuccess(true);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * For "forgot password" or "reset password" flows.
     */
    public void forgotPassword(String email, PasswordResetCallback callback) {
        apiImpl.forgotPassword(email, new TherapyApiImpl.ApiCallback<PasswordResetResponse>() {
            @Override
            public void onSuccess(PasswordResetResponse result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Changes the password using a token from the user,
     * plus a secondary token or code, and the new password itself.
     */
    public void changePassword(String token, String securityToken, String newPassword, PasswordResetCallback callback) {
        apiImpl.changePassword(token, securityToken, newPassword, new TherapyApiImpl.ApiCallback<PasswordResetResponse>() {
            @Override
            public void onSuccess(PasswordResetResponse result) {
                callback.onSuccess(result);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
