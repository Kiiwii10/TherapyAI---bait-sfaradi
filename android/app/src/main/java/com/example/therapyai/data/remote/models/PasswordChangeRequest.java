package com.example.therapyai.data.remote.models;

public class PasswordChangeRequest {
    private String tokenSec;
    private String newPassword;

    public PasswordChangeRequest() {
    }
    public String getTokenSec() {
        return tokenSec;
    }

    public void setTokenSec(String tokenSec) {
        this.tokenSec = tokenSec;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
