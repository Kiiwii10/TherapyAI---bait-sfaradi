package com.example.therapyai.data.remote.models;

import java.util.List;

public class TranscriptDetailResponse {
    private String patientName;
    private String sessionDate; // e.g., "dd-MM-yyyy"
    private String summary;
    private List<TranscriptSentenceResponse> transcript;

    public TranscriptDetailResponse() {
        // Default constructor
    }

    // Getters and Setters
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<TranscriptSentenceResponse> getTranscript() { return transcript; }
    public void setTranscript(List<TranscriptSentenceResponse> transcript) { this.transcript = transcript; }
}
