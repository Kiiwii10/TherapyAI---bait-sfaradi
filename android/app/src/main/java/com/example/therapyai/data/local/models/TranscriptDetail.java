package com.example.therapyai.data.local.models;

import java.util.List;

public class TranscriptDetail {
    private final String patientName;
    private final String sessionDate;
    private final String summary;
    private final List<TranscriptItem> transcriptItems;

    public TranscriptDetail(String patientName, String sessionDate, String summary, List<TranscriptItem> transcriptItems) {
        this.patientName = patientName;
        this.sessionDate = sessionDate;
        this.summary = summary;
        this.transcriptItems = transcriptItems;
    }

    // Getters
    public String getPatientName() { return patientName; }
    public String getSessionDate() { return sessionDate; }
    public String getSummary() { return summary; }
    public List<TranscriptItem> getTranscriptItems() { return transcriptItems; }
}