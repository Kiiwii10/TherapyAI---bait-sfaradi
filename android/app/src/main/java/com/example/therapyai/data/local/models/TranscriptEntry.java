package com.example.therapyai.data.local.models;


public class TranscriptEntry {
    private String speaker;
    private String text;
    private String timestamp;

    public TranscriptEntry(String speaker, String text, String timestamp) {
        this.speaker = speaker;
        this.text = text;
        this.timestamp = timestamp;
    }

    // Getters and Setters (Setters needed for editing)
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public TranscriptEntry clone() {
        return new TranscriptEntry(this.speaker, this.text, this.timestamp);
    }
}