package com.example.therapyai.data.local.models;

import java.util.Objects;
public class TranscriptItem {
    private String speaker;
    private String text;
    private final String timestamp;

    private final String originalSpeaker;
    private final String originalText;

    public TranscriptItem(String speaker, String text, String timestamp) {
        this.speaker = speaker;
        this.text = text;
        this.timestamp = timestamp;
        this.originalSpeaker = speaker;
        this.originalText = text;
    }

    // Getters
    public String getSpeaker() { return speaker; }
    public String getText() { return text; }
    public String getTimestamp() { return timestamp; }
    public String getOriginalSpeaker() { return originalSpeaker; }
    public String getOriginalText() { return originalText; }

    public void setSpeaker(String speaker) { this.speaker = speaker; }
    public void setText(String text) { this.text = text; }

    public boolean hasChanged() {
        return !java.util.Objects.equals(speaker, originalSpeaker) ||
                !java.util.Objects.equals(text, originalText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranscriptItem that = (TranscriptItem) o;
        // Compare the current state fields, NOT the original fields
        return Objects.equals(speaker, that.speaker) &&
                Objects.equals(text, that.text) &&
                Objects.equals(timestamp, that.timestamp);
        // Note: originalSpeaker and originalText are NOT part of the equality check
        // between two potentially different TranscriptItem objects.
    }

    @Override
    public int hashCode() {
        // Use the same fields as equals()
        return Objects.hash(speaker, text, timestamp);
    }
}