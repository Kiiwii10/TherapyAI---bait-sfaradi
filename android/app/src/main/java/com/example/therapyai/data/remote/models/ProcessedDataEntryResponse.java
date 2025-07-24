package com.example.therapyai.data.remote.models;

public class ProcessedDataEntryResponse {
    private String id;
    private String patientName;
    private String sessionDate;
    private String summaryPreview;

    public ProcessedDataEntryResponse() {
        // Default constructor
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getSummaryPreview() { return summaryPreview; }
    public void setSummaryPreview(String summaryPreview) { this.summaryPreview = summaryPreview; }

}