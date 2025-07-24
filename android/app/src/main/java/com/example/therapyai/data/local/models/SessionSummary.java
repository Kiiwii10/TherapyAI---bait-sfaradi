package com.example.therapyai.data.local.models;

import java.util.Objects;

public class SessionSummary {

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


    public SessionSummary(String id,
                          String patientId,
                          String patientName,
                          String therapistId,
                          String therapistName,
                          String title,
                          String date,
                          String descriptionPreview,
                            double positive,
                            double neutral,
                            double negative

    ) {
        this.id = id;
        this.patientId = patientId;
        this.patientName = patientName;
        this.therapistId = therapistId;
        this.therapistName = therapistName;
        this.title = title;
        this.date = date;
        this.descriptionPreview = descriptionPreview;
        this.positive = positive;
        this.neutral = neutral;
        this.negative = negative;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionSummary that = (SessionSummary) o;
        return Objects.equals(id, that.id) && Objects.equals(patientId, that.patientId) && Objects.equals(patientName, that.patientName) && Objects.equals(therapistId, that.therapistId) && Objects.equals(therapistName, that.therapistName) && Objects.equals(title, that.title) && Objects.equals(date, that.date) && Objects.equals(descriptionPreview, that.descriptionPreview);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, patientId, patientName, therapistId, therapistName, title, date, descriptionPreview);
    }

    @Override
    public String toString() {
        return "SessionSummary{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}