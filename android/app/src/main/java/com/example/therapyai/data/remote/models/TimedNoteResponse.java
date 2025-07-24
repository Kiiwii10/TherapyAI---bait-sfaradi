package com.example.therapyai.data.remote.models;

import com.google.gson.annotations.SerializedName;

public class TimedNoteResponse {
    @SerializedName("timestamp")
    private String timestamp;
    @SerializedName("content")
    private String content;

    // Default constructor
    public TimedNoteResponse() {
    }

    // Getters
    public String getTimestamp() { return timestamp; }
    public String getContent() { return content; }
}
