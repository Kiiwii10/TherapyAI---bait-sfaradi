package com.example.therapyai.data.local.models;

public class StatsItem {
    private String title;
    private String value;
    private int iconResourceId;

    public StatsItem(String title, String value, int iconResourceId) {
        this.title = title;
        this.value = value;
        this.iconResourceId = iconResourceId;
    }

    // Getters
    public String getTitle() { return title; }
    public String getValue() { return value; }
    public int getIconResourceId() { return iconResourceId; }
}

