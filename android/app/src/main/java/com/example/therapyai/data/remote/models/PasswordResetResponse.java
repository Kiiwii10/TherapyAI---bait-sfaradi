package com.example.therapyai.data.remote.models;

public class PasswordResetResponse {
    private boolean success;
    private String message;

    public PasswordResetResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
