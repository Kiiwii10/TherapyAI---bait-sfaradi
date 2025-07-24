package com.example.therapyai.data.remote.models;

public class SessionSummaryResponse {
    private String id;
    private String patientId;
    private String patientName;
    private String therapistId;
    private String therapistName;
    private String title;
    private String date;
    private String descriptionPreview;
    private double positive;
    private double neutral;
    private double negative;

    public SessionSummaryResponse() {
    }


    public String getId() { return id; }
    public String getPatientId() { return patientId; }
    public String getPatientName() { return patientName; }
    public String getTherapistId() { return therapistId; }
    public String getTherapistName() { return therapistName; }
    public String getTitle() { return title; }
    public String getDate() { return date; }
    public String getDescriptionPreview() { return descriptionPreview; }


    public void setId(String id) { this.id = id; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public void setTherapistId(String therapistId) { this.therapistId = therapistId; }
    public void setTherapistName(String therapistName) { this.therapistName = therapistName; }
    public void setTitle(String title) { this.title = title; }
    public void setDate(String date) { this.date = date; }
    public void setDescriptionPreview(String descriptionPreview) { this.descriptionPreview = descriptionPreview; }
    public double getPositive() { return positive; }
    public void setPositive(double positive) { this.positive = positive; }
    public double getNeutral() { return neutral; }
    public void setNeutral(double neutral) { this.neutral = neutral; }
    public double getNegative() { return negative; }
    public void setNegative(double negative) { this.negative = negative; }

}