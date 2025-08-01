package com.example.therapyai.data.remote.models;

public class RefreshTokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;

    public RefreshTokenResponse() {
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
}
