package com.example.therapyai.data.remote.models;

import java.util.List;

// TODO: This structure should mirror the JSON provided in the prompt
//       we are using nested classes for clarity corresponding to JSON structure
public class FinalSessionDetailResponse {

    private String sessionId;
    private String therapist_name;
    private String therapist_email;
    private String patient_id;
    private String patient_name;
    private String patient_email;
    private String patient_date_of_birth;
    private String session_date;
    private List<TimedNoteEntry> timed_notes;
    private List<String> general_notes;
    private String summary;

    private double positive;
    private double neutral;
    private double negative;

    public FinalSessionDetailResponse() {
    }


    private List<SentimentScoreEntry> sentiment_scores;

    // Nested class for Timed Notes Entries
    public static class TimedNoteEntry {
        private String timestamp;
        private String content;

        public String getTime() { return timestamp; }
        public void setTime(String time) { this.timestamp = time; }
        public String getNote() { return content; }
        public void setNote(String note) { this.content = note; }
    }

    // Nested class for Sentiment Score Entries (Transcript)
    public static class SentimentScoreEntry {
        private String speaker;
        private String text;
        private String timestamp;
        private double positive;
        private double neutral;
        private double negative;

        public String getSpeaker() { return speaker; }
        public void setSpeaker(String speaker) { this.speaker = speaker; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public double getPositive() { return positive; }
        public void setPositive(double positive) { this.positive = positive; }
        public double getNeutral() { return neutral; }
        public void setNeutral(double neutral) { this.neutral = neutral; }
        public double getNegative() { return negative; }
        public void setNegative(double negative) { this.negative = negative; }
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTherapist_name() { return therapist_name; }
    public void setTherapist_name(String therapist_name) { this.therapist_name = therapist_name; }
    public String getTherapist_email() { return therapist_email; }
    public void setTherapist_email(String therapist_email) { this.therapist_email = therapist_email; }
    public String getPatient_id() { return patient_id; }
    public void setPatient_id(String patient_id) { this.patient_id = patient_id; }
    public String getPatient_name() { return patient_name; }
    public void setPatient_name(String patient_name) { this.patient_name = patient_name; }
    public String getPatient_email() { return patient_email; }
    public void setPatient_email(String patient_email) { this.patient_email = patient_email; }
    public String getSession_date() { return session_date; }
    public String getPatient_date_of_birth() { return patient_date_of_birth; }
    public void setPatient_date_of_birth(String patient_date_of_birth) { this.patient_date_of_birth = patient_date_of_birth; }
    public void setSession_date(String session_date) { this.session_date = session_date; }

    public List<TimedNoteEntry> getTimedNotes() {
        return timed_notes;
    }

    public void setTimedNotes(List<TimedNoteEntry> timed_notes) {
        this.timed_notes = timed_notes;
    }

    public List<String> getGeneral_notes() {
        return general_notes;
    }

    public void setGeneral_notes(List<String> general_notes) {
        this.general_notes = general_notes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public double getPositive() { return positive; }
    public void setPositive(double positive) { this.positive = positive; }
    public double getNeutral() { return neutral; }
    public void setNeutral(double neutral) { this.neutral = neutral; }
    public double getNegative() { return negative; }
    public void setNegative(double negative) { this.negative = negative; }

    public List<SentimentScoreEntry> getSentiment_scores() { return sentiment_scores; }
    public void setSentiment_scores(List<SentimentScoreEntry> sentiment_scores) { this.sentiment_scores = sentiment_scores; }
}