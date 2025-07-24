package com.example.therapyai.data.remote;


import com.example.therapyai.data.remote.models.DeviceRegistrationRequest;
import com.example.therapyai.data.remote.models.LoginRequest;
import com.example.therapyai.data.remote.models.LoginResponse;
import com.example.therapyai.data.remote.models.PasswordChangeRequest;
import com.example.therapyai.data.remote.models.PasswordResetResponse;
import com.example.therapyai.data.remote.models.ProcessedDataEntryResponse;
import com.example.therapyai.data.remote.models.ProfileResponse;
import com.example.therapyai.data.remote.models.RefreshTokenRequest;
import com.example.therapyai.data.remote.models.RefreshTokenResponse;
import com.example.therapyai.data.remote.models.FinalSessionDetailResponse;
import com.example.therapyai.data.remote.models.SessionSubmissionResponse;
import com.example.therapyai.data.remote.models.SessionSummaryResponse;
import com.example.therapyai.data.remote.models.TranscriptDetailResponse;
import com.example.therapyai.data.remote.models.TranscriptSentenceResponse;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TherapyApiService {

    // --- Auth ---
    @POST("auth/login")
    Call<LoginResponse> login(
            @Body LoginRequest request
    );

    @POST("auth/forgot-password") //NOTE: not used, needs to be adjusted based on the facility final DB and auth settings!
    Call<PasswordResetResponse> forgotPassword(
            @Query("email") String email);

    @POST("auth/change-password") //NOTE: not used, needs to be adjusted based on the facility final DB and auth settings!
    Call<PasswordResetResponse> changePassword(
            @Header("Authorization") String token,
            @Body PasswordChangeRequest request
    );

    // Refresh endpoint
    @POST("auth/refresh")
    Call<RefreshTokenResponse> refreshToken(
            @Body RefreshTokenRequest request
    );

    @POST("devices/register")
    Call<Void> registerDevice(
            @Header("Authorization") String token,
            @Body DeviceRegistrationRequest request
    );

    @POST("devices/unregister/{userId}")
    Call<Void> unregisterDevice(
            @Header("Authorization") String token,
            @Path("userId") String userId);

    // --- Browse - profiles & sessions ---

    @GET("profiles/search")
    Call<List<ProfileResponse>> searchProfiles(
            @Header("Authorization") String token,
            @Query("query") String query);

    @GET("profiles/me")
    Call<ProfileResponse> getOwnProfile(
            @Header("Authorization") String token
    );

    @GET("sessions/me")
    Call<List<SessionSummaryResponse>> getOwnSessions(
            @Header("Authorization") String token
    );

    @GET("sessions/search")
    Call<List<SessionSummaryResponse>> searchSessions(
            @Header("Authorization") String token,
            @Query("query") String query);

    @GET("sessions/{sessionId}/details")
    Call<FinalSessionDetailResponse> getSessionDetails(
            @Header("Authorization") String token,
            @Path("sessionId") String sessionId
    );


    // --- Sessions - Upload ---

    @Multipart
    @POST("sessions/upload")
    Call<SessionSubmissionResponse> uploadSessionData(
            @Header("Authorization") String token,
            @Part MultipartBody.Part file,
            @Part("metadata") RequestBody metadata);



    // --- Inbox ---
    @GET("data/pending") // Returns list of items needing review
    Call<List<ProcessedDataEntryResponse>> getPendingData(
            @Header("Authorization") String token);

    // --- Transcript Editing ---
    @GET("data/transcript/{dataId}")
    Call<TranscriptDetailResponse> getSessionTranscriptDetail(
            @Header("Authorization") String token,
            @Path("dataId") String dataId);

    @PUT("data/transcript/{dataId}")
    Call<Void> submitFinalTranscript(
            @Header("Authorization") String token,
            @Path("dataId") String dataId,
            @Body List<TranscriptSentenceResponse> transcriptList);

}
