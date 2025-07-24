package com.example.therapyai.data.local.models;

import java.io.Serializable;
import java.util.List;

public class Notes implements Serializable {
    private List<String> regularNotes;
    private List<TimedNote> timedNotes;
    private String summary;

    public Notes(List<String> regularNotes, List<TimedNote> timedNotes, String summary) {
        this.regularNotes = regularNotes;
        this.timedNotes = timedNotes;
        this.summary = summary;
    }

    // Default constructor for frameworks like Gson
    public Notes() {}

    public List<String> getRegularNotes() {
        return regularNotes;
    }

    public void setRegularNotes(List<String> regularNotes) {
        this.regularNotes = regularNotes;
    }

    public List<TimedNote> getTimedNotes() {
        return timedNotes;
    }

    public void setTimedNotes(List<TimedNote> timedNotes) {
        this.timedNotes = timedNotes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}