package com.example.therapyai.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.therapyai.TherapyAIApp;
import com.example.therapyai.data.local.EphemeralPrefs;
import com.example.therapyai.data.local.SessionManager;
// Import NEW response/request models
import com.example.therapyai.data.remote.models.ProfileResponse;
import com.example.therapyai.data.remote.models.FinalSessionDetailResponse;
import com.example.therapyai.data.remote.models.SessionSummaryResponse;
// Import necessary existing models
import com.example.therapyai.data.remote.models.DeviceRegistrationRequest;
import com.example.therapyai.data.remote.models.LoginRequest;
import com.example.therapyai.data.remote.models.LoginResponse;
import com.example.therapyai.data.remote.models.PasswordChangeRequest;
import com.example.therapyai.data.remote.models.PasswordResetResponse;
import com.example.therapyai.data.remote.models.ProcessedDataEntryResponse;
import com.example.therapyai.data.remote.models.SessionSubmissionResponse;
import com.example.therapyai.data.remote.models.TranscriptSentenceResponse;
import com.example.therapyai.data.remote.models.TranscriptDetailResponse;
import com.google.gson.Gson; // Keep if used for metadata/mocking

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TherapyApiImpl {

    private static final String TAG = "TherapyApiImpl";
    private static final String MOCK_TAG = "TherapyApiImpl_Mock";
    private static TherapyApiImpl instance;
    private final TherapyApiService apiService;
    private final boolean useMockData;
    private final Handler mockHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mockExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private TherapyApiImpl(TherapyApiService apiService, boolean useMockData) {
        this.apiService = apiService;
        this.useMockData = useMockData;
    }

    public static synchronized TherapyApiImpl getInstance(boolean useMockData) {
        if (instance == null) {
            instance = new TherapyApiImpl(ApiServiceProvider.getInstance(), useMockData);
        }
        return instance;
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    private <T> Callback<T> createRetrofitCallback(final ApiCallback<T> customCallback, final String methodName) {
        return new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    customCallback.onSuccess(response.body());
                } else if (response.isSuccessful() && response.body() == null && response.code() >= 200 && response.code() < 300) {
                    // Handle successful responses with no body (e.g., 204 No Content for Void calls)
                    // Need to cast null carefully based on expected <T> type if it's Void.
                    if (customCallback != null) {
                        try {
                            // It works for ApiCallback<Void>.
                            // If T is something else, this might crash, but shouldn't happen for 2xx + null body unless API spec is weird.
                            customCallback.onSuccess(null);
                        } catch (ClassCastException e) {
                            Log.e(TAG, "Callback type mismatch for null body success response in " + methodName, e);
                            customCallback.onError("Unexpected null body for successful response: " + response.code());
                        }
                    }
                }
                else if (response.code() == 401) {
                    // Attempted refresh likely failed or wasn't applicable
                    SessionManager.getInstance().forceLogout();
                    customCallback.onError("Authentication error ("+ response.code() +"). Please log in again.");
                }
                else {
                    String errorMsg = "API call '" + methodName + "' failed: HTTP " + response.code() + " " + response.message();
                    String errorBodyContent = "";
                    if (response.errorBody() != null) {
                        try {
                            errorBodyContent = response.errorBody().string();
                            if (!errorBodyContent.isEmpty()) {
                                errorMsg += " - Body: " + errorBodyContent;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error body for " + methodName, e);
                        }
                    }
                    Log.e(TAG, errorMsg);
                    customCallback.onError(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                String networkErrorMsg = "Network error during API call '" + methodName + "': " + t.getMessage();
                Log.e(TAG, networkErrorMsg, t);
                customCallback.onError(networkErrorMsg);
            }
        };
    }



    public void loginUser(String username, String password, ApiCallback<LoginResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for loginUser.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockLoginResponse(username)), 300);
            return;
        }
        LoginRequest request = new LoginRequest(username, password);
        apiService.login(request).enqueue(createRetrofitCallback(callback, "loginUser"));
    }

    public void forgotPassword(String email, ApiCallback<PasswordResetResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for forgotPassword.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockPasswordResetResponse()), 300);
            return;
        }
        apiService.forgotPassword(email).enqueue(createRetrofitCallback(callback, "forgotPassword"));
    }

    public void changePassword(String token, String secToken, String newPassword, ApiCallback<PasswordResetResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for changePassword.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockPasswordResetResponse()), 300);
            return;
        }
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setTokenSec(secToken);
        request.setNewPassword(newPassword);
        apiService.changePassword("Bearer " + token, request)
                .enqueue(createRetrofitCallback(callback, "changePassword"));
    }

    public void registerDevice(String token, DeviceRegistrationRequest request, ApiCallback<Void> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for registerDevice. User: " + request.getUser_id());
            mockHandler.postDelayed(() -> {
                Log.d(MOCK_TAG, "Mock device registration successful.");
                callback.onSuccess(null);
            }, 400);
            return;
        }
        apiService.registerDevice("Bearer " + token, request).enqueue(createRetrofitCallback(callback, "registerDevice"));
    }

    public void unregisterDevice(String token, String userId, ApiCallback<Void> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for unregisterDevice. User: " + userId);
            mockHandler.postDelayed(() -> {
                Log.d(MOCK_TAG, "Mock device unregistration successful.");
                callback.onSuccess(null);
            }, 300);
            return;
        }
        apiService.unregisterDevice("Bearer " + token, userId).enqueue(createRetrofitCallback(callback, "unregisterDevice"));
    }

    // --- Search Methods ---

    public void searchProfiles(String query, ApiCallback<List<ProfileResponse>> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for searchProfiles.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockProfiles(query)), 500);
            return;
        }
        String token = EphemeralPrefs.getInstance().getSessionToken(); // Check if still valid approach
        if (token == null || token.isEmpty()) {
            callback.onError("Authentication token not found. Please log in.");
            return;
        }
        apiService.searchProfiles("Bearer " + token, query)
                .enqueue(createRetrofitCallback(callback, "searchProfiles"));
    }

    public void searchSessions(String query, ApiCallback<List<SessionSummaryResponse>> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for searchSessions.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockSessionSummaries(query)), 600);
            return;
        }
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null || token.isEmpty()) {
            callback.onError("Authentication token not found. Please log in.");
            return;
        }
        // Call the updated API endpoint
        apiService.searchSessions("Bearer " + token, query)
                .enqueue(createRetrofitCallback(callback, "searchSessions"));
    }

    public void getOwnProfile(ApiCallback<ProfileResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for getOwnProfile.");
            // Simulate fetching profile based on logged-in user (use a fixed mock ID for simulation)
            String mockPatientId = "patient_mockUser123"; // Example mock ID
            mockHandler.postDelayed(() -> callback.onSuccess(getMockOwnProfile(mockPatientId)), 350);
            return;
        }
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null || token.isEmpty()) {
            callback.onError("Authentication token not found. Please log in.");
            return;
        }
        apiService.getOwnProfile("Bearer " + token)
                .enqueue(createRetrofitCallback(callback, "getOwnProfile"));
    }

    public void getOwnSessions(ApiCallback<List<SessionSummaryResponse>> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for getOwnSessions.");
            // Simulate fetching sessions based on logged-in user
            String mockPatientId = "patient_mockUser123"; // Use the same mock ID
            mockHandler.postDelayed(() -> callback.onSuccess(getMockOwnSessions(mockPatientId)), 550);
            return;
        }
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null || token.isEmpty()) {
            callback.onError("Authentication token not found. Please log in.");
            return;
        }
        apiService.getOwnSessions("Bearer " + token)
                .enqueue(createRetrofitCallback(callback, "getOwnSessions"));
    }

    // --- Session Detail Method ---

    public void getSessionDetails(String sessionId,
                                  ApiCallback<FinalSessionDetailResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for getSessionDetails (ID: " + sessionId + ").");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockSessionDetail(sessionId)), 700);
            return;
        }
        String token = EphemeralPrefs.getInstance().getSessionToken();
        if (token == null || token.isEmpty()) {
            callback.onError("Authentication token not found. Please log in.");
            return;
        }
        apiService.getSessionDetails("Bearer " + token, sessionId)
                .enqueue(createRetrofitCallback(callback, "getSessionDetails"));
    }

    // --- Session Submission Method ---
    
    public void uploadSessionData(String token,
                                  MultipartBody.Part file,
                                  RequestBody metadata,
                                  ApiCallback<SessionSubmissionResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using MOCK data for uploadSessionData. WILL SAVE FINAL AUDIO TO LOCAL FILE.");

            // Execute the entire mock process on a background thread.
            mockExecutor.execute(() -> {
                try {
                    // This logic is now running in the background.
                    Context context = TherapyAIApp.getInstance();
                    if (context == null) {
                        throw new IllegalStateException("Context is null in EphemeralPrefs. Cannot save mock file.");
                    }
                    File outputDir = context.getExternalCacheDir();
                    if (outputDir == null) outputDir = context.getCacheDir();

                    File outputFile = new File(outputDir, "mock_upload_" + System.currentTimeMillis() + ".wav");
                    Log.d(MOCK_TAG, "Background task: Saving mock upload to: " + outputFile.getAbsolutePath());

                    Buffer buffer = new Buffer();

                    // CRITICAL: This blocking call now runs on the background thread.
                    // The progress callbacks inside it will call LiveData.postValue(),
                    // which is thread-safe and will trigger UI updates on the main thread.
                    file.body().writeTo(buffer);

                    // Save the result to a file.
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(buffer.readByteArray());
                    }

                    String successMsg = "Mock upload successful. Final audio saved to: " + outputFile.getName();
                    Log.i(MOCK_TAG, successMsg + " (Size: " + outputFile.length() + " bytes)");

                    // Post the SUCCESS result back to the main UI thread.
                    mainThreadHandler.post(() -> {
                        Toast.makeText(context, "Mock Success: Audio saved.", Toast.LENGTH_SHORT).show();
                        SessionSubmissionResponse mockResponse = new SessionSubmissionResponse(
                                "mock-session-" + UUID.randomUUID().toString(),
                                successMsg
                        );
                        callback.onSuccess(mockResponse);
                    });

                } catch (Exception e) {
                    Log.e(MOCK_TAG, "Failed to save mock upload to file", e);
                    // Post the ERROR result back to the main UI thread.
                    mainThreadHandler.post(() -> callback.onError("Mock upload failed: " + e.getMessage()));
                }
            });

            return; // Return immediately, the work is happening in the background.
        }

        apiService.uploadSessionData("Bearer " + token, file, metadata)
                .enqueue(createRetrofitCallback(callback, "uploadSessionData"));
    }

    public void refreshPendingData(String token, final ApiCallback<List<ProcessedDataEntryResponse>> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for refreshPendingData.");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockPendingDataList()), 600);
            return;
        }
        apiService.getPendingData(token).enqueue(createRetrofitCallback(callback, "refreshPendingData"));
    }


    // --- Transcript Editing ---

    public void getSessionTranscriptDetail(String token,
                                           String dataId,
                                           final ApiCallback<TranscriptDetailResponse> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for getSessionTranscriptDetail (ID: " + dataId + ").");
            mockHandler.postDelayed(() -> callback.onSuccess(getMockTranscriptDetailResponse(dataId)), 800);
            return;
        }
        apiService.getSessionTranscriptDetail("Bearer " + token, dataId).enqueue(createRetrofitCallback(callback, "getSessionTranscriptDetail"));
    }

    public void submitFinalTranscript(String token, String dataId, List<TranscriptSentenceResponse> transcriptList, final ApiCallback<Void> callback) {
        if (useMockData) {
            Log.d(MOCK_TAG, "Using mock data for submitFinalTranscript (ID: " + dataId + ").");
            mockHandler.postDelayed(() -> callback.onSuccess(null), 400);
            return;
        }
        apiService.submitFinalTranscript("Bearer " + token, dataId, transcriptList).enqueue(createRetrofitCallback(callback, "submitFinalTranscript"));
    }



    // ================================================================
    // MOCK DATA UTILS - **UPDATE THESE THOROUGHLY**
    // ================================================================

    private LoginResponse getMockLoginResponse(String username) {
        // Keep this structure, adjust roles if needed
        LoginResponse response = new LoginResponse();
        response.setToken("mock_token_" + UUID.randomUUID().toString());
        response.setRefreshToken("mock_refresh_" + UUID.randomUUID().toString());
        response.setUserId(username.startsWith("pa") ? "patient_" + username : "therapist_" + username);
        response.setUserFullName("John Doe");
        response.setDateOfBirth("1990-01-01"); // Example
        response.setUserType(username.startsWith("pa") ? "PATIENT" : "THERAPIST");
        return response;
    }

    private PasswordResetResponse getMockPasswordResetResponse() {
        PasswordResetResponse response = new PasswordResetResponse();
        response.setSuccess(true);
        response.setMessage("Operation completed successfully (mock)");
        return response;
    }

    private List<ProfileResponse> getMockProfiles(String query) {
        List<ProfileResponse> mockProfiles = new ArrayList<>();
        int count = query.isEmpty() ? 5 : 2; // Fewer results if query exists
        for (int i = 1; i <= count; i++) {
            ProfileResponse profile = new ProfileResponse();
            String patientId = "mock_P_" + (100 + i);
            profile.setId(patientId);
            profile.setFullName("Patient " + query + " " + i);
            profile.setPictureUrl("https://i.pravatar.cc/150?img=" + i); // Placeholder image service
            profile.setDateOfBirth(getRandomDate(2000, 365 * 2));
            // Give some mock session IDs associated with this profile
//            List<String> sessionIds = new ArrayList<>();
//            sessionIds.add("mock_S_" + patientId + "_1");
//            sessionIds.add("mock_S_" + patientId + "_2");
//            profile.setDataEntryIds(sessionIds);
            mockProfiles.add(profile);


//            profile.setDataEntryIds(sessionIds); // Keep setting this if ProfileResponse requires it

            mockProfiles.add(profile);
        }
        Log.d(MOCK_TAG, "Generated " + mockProfiles.size() + " mock profiles for query: " + query);

        Log.d(MOCK_TAG, "Generated " + mockProfiles.size() + " mock profiles for query: " + query);
        return mockProfiles;

    }

    private ProfileResponse getMockOwnProfile(String patientId) {
        ProfileResponse profile = new ProfileResponse();
        profile.setId(patientId); // Use the provided ID
        profile.setFullName("Mock Patient User"); // Example name
        profile.setEmail(patientId + "@mockdomain.com");
        profile.setPictureUrl("https://i.pravatar.cc/150?img=5"); // Example image
        profile.setDateOfBirth("1995-05-15");
        Log.d(MOCK_TAG, "Generated mock own profile for ID: " + patientId);
        return profile;
    }

    private List<SessionSummaryResponse> getMockOwnSessions(String patientId) {
        List<SessionSummaryResponse> mockSessions = new ArrayList<>();
        int count = 5; // Give the mock patient 5 sessions
        for (int i = 1; i <= count; i++) {
            SessionSummaryResponse entry = new SessionSummaryResponse();
            String sessionId = "mock_S_" + patientId + "_" + i;
            entry.setId(sessionId);
            entry.setPatientId(patientId); // Correct patient ID
            entry.setPatientName("Mock Patient User"); // Consistent name
            entry.setTherapistId("mock_T_DrSmith");
            entry.setTherapistName("Dr. Smith");
            entry.setTitle("My Session #" + i);
            entry.setDate("2024-03-" + (20 + i)); // Example dates
            entry.setDescriptionPreview("Focus on technique " + i + ", discussed weekly progress...");

            double neg = Math.random() * 0.4; // More likely neutral/positive
            double pos = Math.random() * 0.6;
            if (i == 1 || i == 2) neg = Math.random() * 0.6; // More negative early on
            if (i >= 4) pos = 0.4 + Math.random() * 0.6; // More positive later

            double neu = 1.0 - neg - pos;
            if (neu < 0) { // Basic normalization if sum > 1
                double total = neg+pos;
                neg = neg/total;
                pos = pos/total;
                neu = 0;
            }

            entry.setPositive(pos);
            entry.setNeutral(neu);
            entry.setNegative(neg);



            mockSessions.add(entry);
        }
        Log.d(MOCK_TAG, "Generated " + mockSessions.size() + " mock own sessions for patient ID: " + patientId);
        return mockSessions;
    }

    private List<SessionSummaryResponse> getMockSessionSummaries(String query) {
        List<SessionSummaryResponse> mockEntries = new ArrayList<>();
        int count = query.isEmpty() ? 8 : 3;
        for (int i = 1; i <= count; i++) {
            SessionSummaryResponse entry = new SessionSummaryResponse();
            String patientId = "mock_P_" + (100 + (i % 3));
            String sessionId = "mock_S_" + patientId + "_" + i;
            entry.setId(sessionId);
            entry.setPatientId(patientId);
            entry.setPatientName("Patient " + (100 + (i % 3)));
            entry.setTherapistId("mock_T_DrSmith");
            entry.setTherapistName("Dr. Smith");
            entry.setTitle("Session " + query + " #" + i + " with Patient " + (100 + (i % 3)));
            entry.setDate("2024-04-" + (10 + i));
            entry.setDescriptionPreview("Discussed progress on goals, explored coping mechanisms related to " + query + "...");

            double neg = Math.random() * 0.4; // More likely neutral/positive
            double pos = Math.random() * 0.6;
            if (i == 1 || i == 2) neg = Math.random() * 0.6; // More negative early on
            if (i >= 4) pos = 0.4 + Math.random() * 0.6; // More positive later

            double neu = 1.0 - neg - pos;
            if (neu < 0) { // Basic normalization if sum > 1
                double total = neg+pos;
                neg = neg/total;
                pos = pos/total;
                neu = 0;
            }

            entry.setPositive(pos);
            entry.setNeutral(neu);
            entry.setNegative(neg);

            mockEntries.add(entry);
        }
        Log.d(MOCK_TAG, "Generated " + mockEntries.size() + " mock session summaries for query: " + query);
        return mockEntries;
    }

    private FinalSessionDetailResponse getMockSessionDetail(String sessionId) {
        FinalSessionDetailResponse detail = new FinalSessionDetailResponse();
        detail.setSessionId(sessionId);
        String patientId = "mock_P_Unknown";
        String[] parts = sessionId.split("_");
        if (parts.length >= 4 && parts[2].equals("P")) {
            patientId = parts[1] + "_" + parts[2] + "_" + parts[3];
        }

        detail.setPatient_id(patientId);
        detail.setPatient_name("Mock Patient " + patientId.substring(patientId.length()-3)); // Extract number
        detail.setPatient_email(patientId + "@mock.com");
        detail.setTherapist_name("Dr. Mock Therapist");
        detail.setTherapist_email("dr.mock@therapy.ai");
        detail.setSession_date((10 + (sessionId.hashCode() % 15)) + "-03-2024");

        // Notes

        List<FinalSessionDetailResponse.TimedNoteEntry> timedNotes = new ArrayList<>();
        FinalSessionDetailResponse.TimedNoteEntry tne1 = new FinalSessionDetailResponse.TimedNoteEntry();
        tne1.setTime("00:05:30"); // 5 min 30 sec
        tne1.setNote("Patient described physical symptoms of anxiety (racing heart).");
        timedNotes.add(tne1);
        FinalSessionDetailResponse.TimedNoteEntry tne2 = new FinalSessionDetailResponse.TimedNoteEntry();
        tne2.setTime("00:15:10");
        tne2.setNote("Identified 'catastrophizing' thought pattern.");
        timedNotes.add(tne2);
        FinalSessionDetailResponse.TimedNoteEntry tne3 = new FinalSessionDetailResponse.TimedNoteEntry();
        tne3.setTime("00:35:00");
        tne3.setNote("Discussed thought challenging techniques. Patient seemed responsive.");
        timedNotes.add(tne3);
        detail.setTimedNotes(timedNotes);

        List<String> generalNotes = new ArrayList<>();
        generalNotes.add("Session started on time.");
        generalNotes.add("Patient reported feeling anxious about upcoming presentation.");
        generalNotes.add("Explored cognitive distortions related to the anxiety.");
        detail.setGeneral_notes(generalNotes);

        detail.setSummary("Productive session focusing on anxiety management for the presentation. Patient engaged well with identifying and challenging negative thoughts. Plan: Practice techniques discussed.");

        // Transcript / Sentiment Scores
        List<FinalSessionDetailResponse.SentimentScoreEntry> transcript = new ArrayList<>();
        String[] therapistLines = {"Good morning. How have you been feeling since our last session?", "Okay, tell me more about that anxiety.", "That sounds challenging. What thoughts go through your mind?", "I hear that. It sounds like a pattern of catastrophizing.", "Let's try a technique to challenge that thought. What's the evidence against it?", "That's a good start. What's a more balanced thought?", "Good morning. How have you been feeling since our last session?", "Okay, tell me more about that anxiety.", "That sounds challenging. What thoughts go through your mind?", "I hear that. It sounds like a pattern of catastrophizing.", "Let's try a technique to challenge that thought. What's the evidence against it?", "That's a good start. What's a more balanced thought?"};
        String[] patientLines = {"Okay, I guess. A bit anxious about the presentation next week.", "It's just... overwhelming. I keep thinking I'll mess up completely.", "Like, 'Everyone will think I'm incompetent', 'I'll forget everything'.", "Yeah... I guess I do jump to the worst conclusion.", "Well... I have prepared a lot. And I've given okay presentations before.", "Maybe... 'It's normal to be nervous, but I'm prepared and can handle it.'", "Okay, I guess. A bit anxious about the presentation next week.", "It's just... overwhelming. I keep thinking I'll mess up completely.", "Like, 'Everyone will think I'm incompetent', 'I'll forget everything'.", "Yeah... I guess I do jump to the worst conclusion.", "Well... I have prepared a lot. And I've given okay presentations before.", "Maybe... 'It's normal to be nervous, but I'm prepared and can handle it.'" };
        float baseTime = 0;
        for (int i = 0; i < Math.min(therapistLines.length, patientLines.length); i++) {
            FinalSessionDetailResponse.SentimentScoreEntry entryTherapist = new FinalSessionDetailResponse.SentimentScoreEntry();
            entryTherapist.setText(therapistLines[i]);
            entryTherapist.setTimestamp(String.format("%.1f", baseTime)); // Time in seconds
            baseTime += 5.0 + Math.random() * 15.0; // Add 5-20 sec
            entryTherapist.setSpeaker("Therapist");
            entryTherapist.setPositive(0.0);
            entryTherapist.setNeutral(1.0);
            entryTherapist.setNegative(0.0);
            transcript.add(entryTherapist);

            FinalSessionDetailResponse.SentimentScoreEntry entryPatient = new FinalSessionDetailResponse.SentimentScoreEntry();
            entryPatient.setText(patientLines[i]);
            entryPatient.setTimestamp(String.format("%.1f", baseTime));
            baseTime += 10.0 + Math.random() * 40.0; // Add 10-50 sec
            entryPatient.setSpeaker("Patient");

            // Assign mock sentiments (make them vary a bit)
            double neg = Math.random() * 0.4; // More likely neutral/positive
            double pos = Math.random() * 0.6;
            if (i == 1 || i == 2) neg = Math.random() * 0.6; // More negative early on
            if (i >= 4) pos = 0.4 + Math.random() * 0.6; // More positive later

            double neu = 1.0 - neg - pos;
            if (neu < 0) { // Basic normalization if sum > 1
                double total = neg+pos;
                neg = neg/total;
                pos = pos/total;
                neu = 0;
            }

            entryPatient.setNegative(neg);
            entryPatient.setNeutral(neu);
            entryPatient.setPositive(pos);
            transcript.add(entryPatient);
        }
        detail.setSentiment_scores(transcript);

        Log.d(MOCK_TAG, "Generated mock session detail for ID: " + sessionId);
        return detail;
    }


    private List<ProcessedDataEntryResponse> getMockPendingDataList() {
        List<ProcessedDataEntryResponse> mockList = new ArrayList<>();

        ProcessedDataEntryResponse entry1 = new ProcessedDataEntryResponse();
        entry1.setId("mock_transcript_id_001");
        entry1.setPatientName("Adams, Paul");
        entry1.setSessionDate("15-03-2024");
        entry1.setSummaryPreview("Patient discussed project deadline stress, exploring overwhelming feelings...");
        mockList.add(entry1);

        ProcessedDataEntryResponse entry2 = new ProcessedDataEntryResponse();
        entry2.setId("mock_transcript_id_002");
        entry2.setPatientName("Barker, Chloe");
        entry2.setSessionDate("14-03-2024");
        entry2.setSummaryPreview("Followed up on boundary setting. Patient reported one success but felt guilty...");
        mockList.add(entry2);

        ProcessedDataEntryResponse entry3 = new ProcessedDataEntryResponse();
        entry3.setId("mock_transcript_id_003");
        entry3.setPatientName("Edwards, David");
        entry3.setSessionDate("12-03-2024");
        entry3.setSummaryPreview("Patient reported feeling somewhat better this week. Explored contributing factors...");
        mockList.add(entry3);

        Log.d(MOCK_TAG, "Generated " + mockList.size() + " mock pending items.");
        return mockList;
    }

    private TranscriptDetailResponse getMockTranscriptDetailResponse(String dataId) {
        TranscriptDetailResponse response = new TranscriptDetailResponse();
        List<TranscriptSentenceResponse> transcriptList = new ArrayList<>();

        switch (dataId) {
            case "mock_transcript_id_001":
                response.setPatientName("Adams, Paul");
                response.setSessionDate("15-03-2024");
                response.setSummary("Session focused on managing stress related to an upcoming project deadline. Patient described feelings of being overwhelmed due to work volume and unclear direction. Explored breaking tasks down into smaller, manageable steps as a coping strategy.");

                transcriptList.add(createTranscriptLine("Patient", "I've been struggling with the project deadline.", "00:00:15.123"));
                transcriptList.add(createTranscriptLine("Therapist", "Okay, let's unpack that. What specifically feels overwhelming?", "00:00:22.456"));
                transcriptList.add(createTranscriptLine("Patient", "Just the sheer volume of work and the lack of clear direction.", "00:00:30.789"));
                transcriptList.add(createTranscriptLine("Therapist", "That sounds stressful. Have you tried breaking it down into smaller steps?", "00:00:45.012"));
                transcriptList.add(createTranscriptLine("Patient", "Not yet, I didn't know where to start.", "00:00:51.345"));
                break;
            case "mock_transcript_id_002":
                response.setPatientName("Barker, Chloe");
                response.setSessionDate("14-03-2024");
                response.setSummary("Reviewed progress on setting boundaries discussed previously. Patient successfully implemented a boundary once but experienced subsequent guilt. Normalized the feeling of guilt as part of the process and reinforced the success of taking the initial step.");

                transcriptList.add(createTranscriptLine("Therapist", "Last time we talked about setting boundaries. How did that go?", "00:01:10.000"));
                transcriptList.add(createTranscriptLine("Patient", "It was hard. I managed to say no once, but felt guilty.", "00:01:18.500"));
                transcriptList.add(createTranscriptLine("Therapist", "That's a significant first step! Acknowledge that success. The guilt is normal initially.", "00:01:25.750"));
                transcriptList.add(createTranscriptLine("Patient", "Okay, I guess so.", "00:01:35.250"));
                break;
            case "mock_transcript_id_003":
                response.setPatientName("Edwards, David");
                response.setSessionDate("12-03-2024");
                response.setSummary("Patient reported a slight improvement in mood compared to the previous week. Session involved exploring the potential reasons and factors contributing to this positive shift, reinforcing positive coping mechanisms.");

                transcriptList.add(createTranscriptLine("Patient", "Feeling a bit better this week.", "00:00:05.900"));
                transcriptList.add(createTranscriptLine("Therapist", "That's good to hear. What contributed to that?", "00:00:12.100"));
                transcriptList.add(createTranscriptLine("Patient", "I think getting outside more helped.", "00:00:18.300"));
                break;
            default:
                Log.w(MOCK_TAG, "getMockTranscriptDetailResponse called with unknown mock ID: " + dataId);
                response.setPatientName("Unknown Patient");
                response.setSessionDate("Unknown Date");
                response.setSummary("No summary available for this mock ID.");
                // Return empty transcript list
                break;
        }
        response.setTranscript(transcriptList);
        Log.d(MOCK_TAG, "Generated mock transcript detail response for ID: " + dataId);
        return response;
    }

    // Helper for creating mock transcript lines
    private TranscriptSentenceResponse createTranscriptLine(String speaker, String text, String timestamp) {
        TranscriptSentenceResponse line = new TranscriptSentenceResponse();
        line.setSpeaker(speaker);
        line.setText(text);
        line.setTimestamp(timestamp);
        return line;
    }

    private String getMockTranscriptJsonForEditing(String dataId) {
        String mockJson;
        switch (dataId) {
            case "mock_transcript_id_001":
                mockJson = "[{\"speaker\":\"Patient\",\"text\":\"I've been struggling with the project deadline.\"},"
                        + "{\"speaker\":\"Therapist\",\"text\":\"Okay, let's unpack that. What specifically feels overwhelming?\"},"
                        + "{\"speaker\":\"Patient\",\"text\":\"Just the sheer volume of work and the lack of clear direction.\"},"
                        + "{\"speaker\":\"Therapist\",\"text\":\"That sounds stressful. Have you tried breaking it down into smaller steps?\"},"
                        + "{\"speaker\":\"Patient\",\"text\":\"Not yet, I didn't know where to start.\"}]";
                break;
            case "mock_transcript_id_002":
                mockJson = "[{\"speaker\":\"Therapist\",\"text\":\"Last time we talked about setting boundaries. How did that go?\"},"
                        + "{\"speaker\":\"Patient\",\"text\":\"It was hard. I managed to say no once, but felt guilty.\"},"
                        + "{\"speaker\":\"Therapist\",\"text\":\"That's a significant first step! Acknowledge that success. The guilt is normal initially.\"},"
                        + "{\"speaker\":\"Patient\",\"text\":\"Okay, I guess so.\"}]";
                break;
            case "mock_transcript_id_003":
                mockJson = "[{\"speaker\":\"Patient\",\"text\":\"Feeling a bit better this week.\"},"
                        + "{\"speaker\":\"Therapist\",\"text\":\"That's good to hear. What contributed to that?\"}]";
                break;
            default:
                Log.w(MOCK_TAG, "getMockTranscriptJson called with unknown mock ID: " + dataId);
                mockJson = "[]"; // Return empty JSON array
                break;
        }
        Log.d(MOCK_TAG, "Generated mock transcript JSON for ID: " + dataId);
        return mockJson;
    }

    private String getRandomDate(int yearStart, int daysAgoMax) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.YEAR, yearStart + (int)(Math.random() * (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - yearStart + 1)));
        cal.set(java.util.Calendar.DAY_OF_YEAR, 1 + (int)(Math.random() * cal.getActualMaximum(java.util.Calendar.DAY_OF_YEAR)));
        // Or make dates sequential going back
        // cal.add(Calendar.DAY_OF_YEAR, - (int)(Math.random() * daysAgoMax));
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        return sdf.format(cal.getTime());
    }

}