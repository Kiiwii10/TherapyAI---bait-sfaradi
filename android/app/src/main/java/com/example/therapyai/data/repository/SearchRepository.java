package com.example.therapyai.data.repository;

import android.util.Log;

import com.example.therapyai.data.local.models.Profile;
import com.example.therapyai.data.local.models.FinalSessionDetail;
import com.example.therapyai.data.local.models.SessionSummary;
import com.example.therapyai.data.local.models.SentimentScore;
import com.example.therapyai.data.local.models.TimedNote;
import com.example.therapyai.data.local.models.FinalTranscriptEntry;
import com.example.therapyai.data.remote.models.ProfileResponse;
import com.example.therapyai.data.remote.models.FinalSessionDetailResponse;
import com.example.therapyai.data.remote.models.SessionSummaryResponse;
import com.example.therapyai.data.remote.TherapyApiImpl;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchRepository {

    private static final String TAG = "SearchRepository";
    private static SearchRepository instance;
    private final TherapyApiImpl apiImpl;

    private SearchRepository(TherapyApiImpl apiImpl) {
        this.apiImpl = apiImpl;
    }

    public static synchronized void init(boolean useMockData) {
        if (instance == null) {
            instance = new SearchRepository(TherapyApiImpl.getInstance(useMockData));
        }
    }

    public static synchronized SearchRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SearchRepository not initialized!");
        }
        return instance;
    }


    // Callback for profile search results
    public interface ProfileSearchCallback {
        void onProfilesFound(List<Profile> profiles);
        void onError(String error);
    }

    // Callback for session summary search results
    public interface SessionSearchCallback {
        void onSessionsFound(List<SessionSummary> sessionSummaries);
        void onError(String error);
    }

    // Callback for fetching full session details
    public interface SessionDetailCallback {
        void onSessionDetailFound(FinalSessionDetail finalSessionDetail);
        void onError(String error);
    }

    public interface ProfileCallback {
        void onProfileFound(Profile profile);
        void onError(String error);
    }




    public void performProfileSearch(String query, ProfileSearchCallback callback) {
        apiImpl.searchProfiles(query, new TherapyApiImpl.ApiCallback<List<ProfileResponse>>() {
            @Override
            public void onSuccess(List<ProfileResponse> result) {
                if (result == null) {
                    Log.w(TAG, "Profile search returned null list.");
                    callback.onProfilesFound(new ArrayList<>()); // Return empty list
                    return;
                }
                List<Profile> profiles = convertToProfiles(result);
                Log.d(TAG, "Profile search success. Found " + profiles.size() + " profiles.");
                callback.onProfilesFound(profiles);
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "Profile search failed: " + errorMsg);
                callback.onError(errorMsg);
            }
        });
    }

    public void performSessionSearch(String query, SessionSearchCallback callback) {
        apiImpl.searchSessions(query, new TherapyApiImpl.ApiCallback<List<SessionSummaryResponse>>() {
            @Override
            public void onSuccess(List<SessionSummaryResponse> result) {
                if (result == null) {
                    Log.w(TAG, "Session search returned null list.");
                    callback.onSessionsFound(new ArrayList<>()); // Return empty list
                    return;
                }
                List<SessionSummary> summaries = convertToSessionSummaries(result);
                Log.d(TAG, "Session search success. Found " + summaries.size() + " summaries.");
                callback.onSessionsFound(summaries);
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "Session search failed: " + errorMsg);
                callback.onError(errorMsg);
            }
        });
    }

    public void getSessionDetails(String sessionId, SessionDetailCallback callback) {
        apiImpl.getSessionDetails(sessionId, new TherapyApiImpl.ApiCallback<FinalSessionDetailResponse>() {
            @Override
            public void onSuccess(FinalSessionDetailResponse result) {
                if (result == null) {
                    Log.e(TAG, "getSessionDetails returned null for ID: " + sessionId);
                    callback.onError("Failed to retrieve session details (null response).");
                    return;
                }
                try {
                    FinalSessionDetail detail = convertToSessionDetail(result);
                    Log.d(TAG, "Session detail fetch success for ID: " + sessionId);
                    callback.onSessionDetailFound(detail);
                } catch (Exception e) {
                    Log.e(TAG, "Error converting SessionDetailResponse to SessionDetail for ID: " + sessionId, e);
                    callback.onError("Failed to process session details: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "Session detail fetch failed for ID " + sessionId + ": " + errorMsg);
                callback.onError(errorMsg);
            }
        });
    }

    // ----------- patient calls -------------

    public void getPatientProfile(ProfileCallback callback) {
        apiImpl.getOwnProfile(new TherapyApiImpl.ApiCallback<ProfileResponse>() {
            @Override
            public void onSuccess(ProfileResponse result) {
                if (result == null) {
                    Log.e(TAG, "getOwnProfile returned null.");
                    callback.onError("Failed to retrieve profile (null response).");
                    return;
                }
                try {
                    // TODO: add convert later for single profile
                    List<ProfileResponse> list = new ArrayList<>();
                    list.add(result);
                    List<Profile> profiles = convertToProfiles(list);
                    if (!profiles.isEmpty()) {
                        Log.d(TAG, "Patient profile fetch success.");
                        callback.onProfileFound(profiles.get(0));
                    } else {
                        Log.e(TAG, "Failed to convert patient profile response.");
                        callback.onError("Failed to process profile data.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error converting ProfileResponse to Profile", e);
                    callback.onError("Failed to process profile data: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "Patient profile fetch failed: " + errorMsg);
                callback.onError(errorMsg);
            }
        });
    }

    public void getPatientSessions(SessionSearchCallback callback) {
        apiImpl.getOwnSessions(new TherapyApiImpl.ApiCallback<List<SessionSummaryResponse>>() {
            @Override
            public void onSuccess(List<SessionSummaryResponse> result) {
                if (result == null) {
                    Log.w(TAG, "Patient session search returned null list.");
                    callback.onSessionsFound(new ArrayList<>());
                    return;
                }
                List<SessionSummary> summaries = convertToSessionSummaries(result);
                Log.d(TAG, "Patient session search success. Found " + summaries.size() + " summaries.");
                callback.onSessionsFound(summaries);
            }

            @Override
            public void onError(String errorMsg) {
                Log.e(TAG, "Patient session search failed: " + errorMsg);
                callback.onError(errorMsg);
            }
        });
    }




    private List<Profile> convertToProfiles(List<ProfileResponse> responses) {
        if (responses == null) return new ArrayList<>();

        return responses.stream()
                .map(response -> {
                    return new Profile(
                            response.getId(),
                            response.getFullName(),
                            response.getEmail(),
                            response.getDateOfBirth(),
                            response.getPictureUrl()
                    );
                })
                .collect(Collectors.toList());
    }

    private List<SessionSummary> convertToSessionSummaries(List<SessionSummaryResponse> responses) {
        if (responses == null) return new ArrayList<>();
        return responses.stream()
                .map(response -> new SessionSummary(
                        response.getId(),
                        response.getPatientId(),
                        response.getPatientName(),
                        response.getTherapistId(),
                        response.getTherapistName(),
                        response.getTitle(),
                        response.getDate(),
                        response.getDescriptionPreview(),
                        response.getPositive(),
                        response.getNeutral(),
                        response.getNegative()
                ))
                .collect(Collectors.toList());
    }

    private FinalSessionDetail convertToSessionDetail(FinalSessionDetailResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Cannot convert null SessionDetailResponse");
        }

        List<String> generalNotes = new ArrayList<>();
        List<TimedNote> timedNotes = new ArrayList<>();
        String summary = response.getSummary() != null ? response.getSummary() : "";

        if (response.getGeneral_notes() != null) {
            generalNotes.addAll(response.getGeneral_notes());
        }
        if (response.getTimedNotes() != null) {
            timedNotes = response.getTimedNotes().stream()
                    .map(tn -> new TimedNote(tn.getTime(), tn.getNote()))
                    .collect(Collectors.toList());
        }

        List<FinalTranscriptEntry> transcriptEntries = new ArrayList<>();
        if (response.getSentiment_scores() != null) {
            transcriptEntries = response.getSentiment_scores().stream()
                    .map(apiEntry -> {
                        SentimentScore score = null;
                        if ("Patient".equalsIgnoreCase(apiEntry.getSpeaker())) {
                            score = new SentimentScore(
                                    (float) apiEntry.getPositive(),
                                    (float) apiEntry.getNeutral(),
                                    (float) apiEntry.getNegative()
                            );
                        }
                        return new FinalTranscriptEntry(
                                apiEntry.getSpeaker(),
                                apiEntry.getText(),
                                apiEntry.getTimestamp(),
                                score // This can be null if speaker is Therapist
                        );
                    })
                    .collect(Collectors.toList());
        }

        return new FinalSessionDetail(
                response.getSessionId(),
                null,
                response.getTherapist_name(),
                response.getTherapist_email(),
                response.getPatient_id(),
                response.getPatient_name(),
                response.getPatient_email(),
                response.getSession_date(),
                generalNotes,
                timedNotes,
                summary,
                response.getPositive(), // Overall session positive
                response.getNeutral(),  // Overall session neutral
                response.getNegative(), // Overall session negative
                transcriptEntries
        );
    }
}