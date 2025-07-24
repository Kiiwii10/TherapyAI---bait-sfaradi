package com.example.therapyai.data.remote.models;

public class DeviceRegistrationRequest {
    private String token;
    private String platform;
    private String user_id;
    private String user_type;

    public DeviceRegistrationRequest() {

    }
    public DeviceRegistrationRequest(String token,
                                     String platform,
                                     String user_id,
                                     String user_type) {
        this.token = token;
        this.platform = platform;
        this.user_id = user_id;
        this.user_type = user_type;
    }
    // Add getters if needed by TherapyApiImpl mock logging
    public String getUser_id() { return user_id; }
    public String getToken() { return token; }
}

