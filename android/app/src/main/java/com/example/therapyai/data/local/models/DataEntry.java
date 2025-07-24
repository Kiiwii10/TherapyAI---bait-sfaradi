package com.example.therapyai.data.local.models;


public class DataEntry {

    private String id;
    private String name;
    private String patientId;
    private String therapistId;
    private String title;
    private String description;
    private String date;
    private String sessionType;
    private int position;

    public DataEntry(String id,
                     String name,
                     String patientId,
                     String therapistId,
                     String title,
                     String description,
                     String date,
                     String sessionType) {
        this.id = id;
        this.name = name;
        this.patientId = patientId;
        this.therapistId = therapistId;
        this.title = title;
        this.description = description;
        this.date = date;
        this.sessionType = sessionType;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public void setTherapistId(String therapistId) {
        this.therapistId = therapistId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getTherapistId() {
        return therapistId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getDate() {
        return date;
    }

    public String getSessionType() {
        return sessionType;
    }

    public int getPosition() {
        return position;
    }
    public void setPosition(int position) {
        this.position = position;
    }
}
