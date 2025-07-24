package com.example.therapyai.data.local.models;

import androidx.annotation.Nullable;

import java.util.Objects;

public class FinalTranscriptEntry {
    private final String speaker;
    private final String text;
    private final String timestamp;
    @Nullable
    private final SentimentScore sentimentScore; // Nullable for therapists

    public FinalTranscriptEntry(String speaker, String text, String timestamp, @Nullable SentimentScore sentimentScore) {
        this.speaker = speaker;
        this.text = text;
        this.timestamp = timestamp;
        this.sentimentScore = sentimentScore;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Nullable
    public SentimentScore getSentimentScore() {
        return sentimentScore;
    }

    // Convenience getters that were used in the old adapter, returning null if not applicable
    // These help reduce changes in the adapter initially, but ideally, the adapter should use getSpeaker() and getText()
    public String getTherapistSentence() {
        return "Therapist".equalsIgnoreCase(speaker) ? text : null;
    }

    public String getTherapistSentenceTime() {
        return "Therapist".equalsIgnoreCase(speaker) ? timestamp : null;
    }

    public String getPatientSentence() {
        return "Patient".equalsIgnoreCase(speaker) ? text : null;
    }

    public String getPatientSentenceTime() {
        return "Patient".equalsIgnoreCase(speaker) ? timestamp : null;
    }
}