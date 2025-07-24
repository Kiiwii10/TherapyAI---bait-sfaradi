package com.example.therapyai.data.remote.models;

public class TranscriptSentenceResponse {
    private String speaker; // "Patient" or "Therapist"
    private String text;
    private String timestamp;

    // Default constructor
    public TranscriptSentenceResponse() {
        // No-arg constructor for serialization/deserialization
    }

    // Getters and Setters
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}