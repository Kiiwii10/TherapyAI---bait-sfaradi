package com.example.therapyai.data.local.models;

import java.util.List;
import java.util.Objects;

public class FinalSessionDetail {
    private String id;
    private String therapistId;
    private String therapistName;
    private String therapistEmail;
    private String patientId;
    private String patientName;
    private String patientEmail;
    private String treatmentDate;
    private List<String> generalNotes;
    private List<TimedNote> timedNotes;
    private String summary;
    private List<FinalTranscriptEntry> transcriptEntries;

    private double positive;
    private double neutral;
    private double negative;

    public FinalSessionDetail(String id,
                              String therapistId,
                              String therapistName,
                              String therapistEmail,
                              String patientId,
                              String patientName,
                              String patientEmail,
                              String treatmentDate,
                              List<String> generalNotes,
                              List<TimedNote> timedNotes,
                              String summary,
                              double positive,
                              double neutral,
                              double negative,
                              List<FinalTranscriptEntry> transcriptEntries) {
        this.id = id;
        this.therapistId = therapistId;
        this.therapistName = therapistName;
        this.therapistEmail = therapistEmail;
        this.patientId = patientId;
        this.patientName = patientName;
        this.patientEmail = patientEmail;
        this.treatmentDate = treatmentDate;
        this.generalNotes = generalNotes;
        this.timedNotes = timedNotes;
        this.summary = summary;
        this.positive = positive;
        this.neutral = neutral;
        this.negative = negative;
        this.transcriptEntries = transcriptEntries;
    }

    public String getId() { return id; }
    public String getTherapistId() { return therapistId; }
    public String getTherapistName() { return therapistName; }
    public String getTherapistEmail() { return therapistEmail; }
    public String getPatientId() { return patientId; }
    public String getPatientName() { return patientName; }
    public String getPatientEmail() { return patientEmail; }
    public String getTreatmentDate() { return treatmentDate; }
    public List<String> getGeneralNotes() { return generalNotes; }
    public List<TimedNote> getTimedNotes() { return timedNotes; }
    public String getSummary() { return summary; }
    public List<FinalTranscriptEntry> getTranscriptEntries() { return transcriptEntries; }

    public void setId(String id) { this.id = id; }
    public void setTherapistId(String therapistId) { this.therapistId = therapistId; }
    public void setTherapistName(String therapistName) { this.therapistName = therapistName; }
    public void setTherapistEmail(String therapistEmail) { this.therapistEmail = therapistEmail; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }
    public void setTreatmentDate(String treatmentDate) { this.treatmentDate = treatmentDate; }
    public void setGeneralNotes(List<String> generalNotes) { this.generalNotes = generalNotes; }
    public void setTimedNotes(List<TimedNote> timedNotes) { this.timedNotes = timedNotes; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setTranscriptEntries(List<FinalTranscriptEntry> transcriptEntries) { this.transcriptEntries = transcriptEntries; }

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
        FinalSessionDetail that = (FinalSessionDetail) o;
        return Objects.equals(id, that.id) && Objects.equals(therapistId, that.therapistId) && Objects.equals(therapistName, that.therapistName) && Objects.equals(therapistEmail, that.therapistEmail) && Objects.equals(patientId, that.patientId) && Objects.equals(patientName, that.patientName) && Objects.equals(patientEmail, that.patientEmail) && Objects.equals(treatmentDate, that.treatmentDate) && Objects.equals(generalNotes, that.generalNotes) && Objects.equals(timedNotes, that.timedNotes) && Objects.equals(summary, that.summary) && Objects.equals(transcriptEntries, that.transcriptEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, therapistId, therapistName, therapistEmail, patientId, patientName, patientEmail, treatmentDate, generalNotes, timedNotes, summary, transcriptEntries);
    }
}