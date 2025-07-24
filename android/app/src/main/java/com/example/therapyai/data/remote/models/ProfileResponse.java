package com.example.therapyai.data.remote.models;

import java.util.List;

public class ProfileResponse {
    private String id;
    private String fullName;
    private String email;
    private String dateOfBirth;
    private String pictureUrl;

    public ProfileResponse() {
        // Default constructor
    }

    // Getters
    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getDateOfBirth() { return dateOfBirth; }
    public String getPictureUrl() { return pictureUrl; }


    public void setId(String id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }
}