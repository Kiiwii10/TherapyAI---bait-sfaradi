package com.example.therapyai.data.local.models;

public class SearchItem {
    public static final int TYPE_HEADER_PROFILES = 0;
    public static final int TYPE_PROFILE         = 1;
    public static final int TYPE_HEADER_SESSIONS = 2;
    public static final int TYPE_SESSION_SUMMARY = 3;

    private int viewType;

    private boolean showProgress;
    private Profile profile;
    private SessionSummary sessionSummary;
    private String headerTitle;

    public SearchItem(int viewType, String headerTitle, boolean showProgress) {
        if (viewType != TYPE_HEADER_PROFILES && viewType != TYPE_HEADER_SESSIONS) {
            throw new IllegalArgumentException("Invalid viewType for header");
        }
        this.viewType = viewType;
        this.headerTitle = headerTitle;
        this.showProgress = showProgress;
    }

    public SearchItem(Profile profile) {
        this.viewType = TYPE_PROFILE;
        this.profile = profile;
        this.showProgress = false;
    }

    public SearchItem(SessionSummary sessionSummary) {
        this.viewType = TYPE_SESSION_SUMMARY;
        this.sessionSummary = sessionSummary;
        this.showProgress = false;
    }

    // Getters
    public int getViewType() {
        return viewType;
    }

    public boolean isShowProgress() {
        return showProgress;
    }

    public Profile getProfile() {
        if (viewType != TYPE_PROFILE) return null;
        return profile;
    }

    public SessionSummary getSessionSummary() {
        if (viewType != TYPE_SESSION_SUMMARY) return null;
        return sessionSummary;
    }

    public String getHeaderTitle() {
        if (viewType != TYPE_HEADER_PROFILES && viewType != TYPE_HEADER_SESSIONS) return null;
        return headerTitle;
    }

    public void setShowProgress(boolean show) {
        if (viewType == TYPE_HEADER_PROFILES || viewType == TYPE_HEADER_SESSIONS) {
            this.showProgress = show;
        } else {

        }
    }
}