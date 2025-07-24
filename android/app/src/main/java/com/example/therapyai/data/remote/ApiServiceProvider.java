package com.example.therapyai.data.remote;

import java.util.Collections;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Provides a singleton instance of {@link TherapyApiService} that automatically
 * refreshes the token on 401 responses, thanks to the {@link TokenAuthenticator}.
 */
public class ApiServiceProvider {
    private static TherapyApiService instance;       // Main service (with TokenAuthenticator)
    private static TherapyApiService authlessService; // For refresh calls only
    private static final String BASE_URL = "https://therapyaiapp-chgaenawcmgcfkev.israelcentral-01.azurewebsites.net";

    public static synchronized TherapyApiService getInstance() {
        if (instance == null) {
            authlessService = buildApiServiceWithoutAuth();

            // Enforce TLS 1.2/1.3
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectionSpecs(Collections.singletonList(
                            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                                    .cipherSuites(
                                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
                                            //TODO: Add more cipher suites if needed
                                    )
                                    .build()
                    ))
                    .authenticator(new TokenAuthenticator(authlessService))
                    .build();

            // Create Retrofit instance
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            instance = retrofit.create(TherapyApiService.class);
        }
        return instance;
    }

    /**
     * A helper that builds a Retrofit instance with no Authenticator or interceptors.
     * This is used only by {@link TokenAuthenticator} to call the refresh endpoint
     * so we don't get stuck in a recursion if refresh also returns 401.
     */
    private static TherapyApiService buildApiServiceWithoutAuth() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(TherapyApiService.class);
    }
}
