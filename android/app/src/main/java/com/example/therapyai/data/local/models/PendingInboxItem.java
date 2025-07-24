package com.example.therapyai.data.local.models;

public class PendingInboxItem {
    private final String id;
    private final String patientName;
    private final String sessionDate;
    private final String summaryPreview;

    public PendingInboxItem(String id, String patientName, String sessionDate, String summaryPreview) {
        this.id = id;
        this.patientName = patientName;
        this.sessionDate = sessionDate;
        this.summaryPreview = summaryPreview;
    }

    // Getters
    public String getId() { return id; }
    public String getPatientName() { return patientName; }
    public String getSessionDate() { return sessionDate; }
    public String getSummaryPreview() { return summaryPreview; }
}
