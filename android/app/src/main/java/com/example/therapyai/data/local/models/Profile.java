package com.example.therapyai.data.local.models;

import java.util.Objects;

public class Profile {

    private String id;
    private String fullName;
    private String email;
    private String dateOfBirth;
    private String pictureUrl;

    public Profile(){}

    public Profile(String id,
                   String fullName,
                   String email,
                   String dateOfBirth,
                   String pictureUrl) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.pictureUrl = pictureUrl;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return
                Objects.equals(id, profile.id) &&
                Objects.equals(fullName, profile.fullName) &&
                Objects.equals(pictureUrl, profile.pictureUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fullName, pictureUrl);
    }

    public String toStringSession() {
        return "{id: " + (id != null ? id : "") + ", " +
               "name: " + (fullName != null ? fullName : "") + ", " +
               "email: " + (email != null ? email : "") + ", " +
               "date_of_birth: " + (dateOfBirth != null ? dateOfBirth : "") + "}";
    }
}