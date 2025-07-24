package com.example.therapyai.data.local.models;

import java.util.Objects;

public class TimedNote {
    private String time;
    private String note;

    public TimedNote(String time, String note) {
        this.time = time;
        this.note = note;
    }    
    public String getTime() { return time; }
    public String getNote() { return note; }
    public void setTime(String time) { this.time = time; }
    public void setNote(String note) { this.note = note; }
    
    public float getTimeInSeconds() {
        try {
            if (time == null || time.isEmpty()) {
                return -1f; // Return error code for null or empty time
            }
            
            String[] parts = time.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            } else if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            }
            else if (parts.length == 1) {
                return Float.parseFloat(parts[0]);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Log error or return default
            return -1f;
        }
        return -1f; // Indicate error
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimedNote timedNote = (TimedNote) o;
        return Objects.equals(time, timedNote.time) && Objects.equals(note, timedNote.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, note);
    }
}