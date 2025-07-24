package com.example.therapyai.data.remote.models;

import com.google.gson.annotations.SerializedName;

public class SessionSubmissionResponse {
    @SerializedName("session_id")
    private String sessionId;

    @SerializedName("status")
    private String status;

    @SerializedName("message")
    private String message;

    public SessionSubmissionResponse() {
    }

    public SessionSubmissionResponse(String sessionId, String msg) {
        this.sessionId = sessionId;
        this.message = msg;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

}