package com.example.therapyai.data.remote;

import android.util.Log;

import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.remote.models.RefreshTokenRequest;
import com.example.therapyai.data.remote.models.RefreshTokenResponse;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;

/**
 * Automatically handles 401 responses by refreshing the token (if possible)
 * and retrying the original request with the new access token.
 */
public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuthenticator";

    private final TherapyApiService apiService;

    public TokenAuthenticator(TherapyApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // To prevent infinite loops, check if we've already tried to authenticate
        if (responseCount(response) >= 2) {
            // If we've made multiple attempts, give up
            Log.w(TAG, "Already attempted token refresh. Giving up.");
            return null;
        }

        // Get the stored refresh token
        String refreshToken = EphemeralPrefs.getInstance().getRefreshToken();
        if (refreshToken == null) {
            Log.w(TAG, "No refresh token stored. Cannot refresh. Logging out user...");
            return null;
        }

        // Attempt to refresh synchronously
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        Call<RefreshTokenResponse> refreshCall = apiService.refreshToken(refreshRequest);

        try {
            retrofit2.Response<RefreshTokenResponse> refreshResponse = refreshCall.execute();
            if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                Log.e(TAG, "Token refresh failed: HTTP " + refreshResponse.code());
                return null;
            }

            // We have a new access token (and possibly a new refresh token)
            RefreshTokenResponse body = refreshResponse.body();
            String newAccessToken   = body.getAccessToken();
            String newRefreshToken  = body.getRefreshToken();

            if (newAccessToken == null) {
                Log.e(TAG, "Refresh response did not include a new access token.");
                return null;
            }

            EphemeralPrefs.getInstance().storeSessionToken(newAccessToken);
            if (newRefreshToken != null) {
                EphemeralPrefs.getInstance().storeRefreshToken(newRefreshToken);
            }

            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + newAccessToken)
                    .build();

        } catch (Exception e) {
            // Network/IO or other errors during refresh
            Log.e(TAG, "Exception while refreshing token: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Utility to check how many times we've already tried to authenticate.
     */
    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}
