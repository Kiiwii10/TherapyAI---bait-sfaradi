package com.example.therapyai.data.local.models;

import com.example.therapyai.data.AudioGraphPoint;

import java.util.List;

public class DataEntryMetadata {
    private String dataEntryId;    // same as DataEntry's id
    private String notes;
    private String summary;
    private List<AudioGraphPoint> graphData;
    private String audioUrl;

    public DataEntryMetadata(){}

    public DataEntryMetadata(String dataEntryId, String notes, String summary, List<AudioGraphPoint> graphData, String audioUrl) {
        this.dataEntryId = dataEntryId;
        this.notes = notes;
        this.summary = summary;
        this.graphData = graphData;
        this.audioUrl = audioUrl;
    }

    public String getDataEntryId() {
        return dataEntryId;
    }

    public void setDataEntryId(String dataEntryId) {
        this.dataEntryId = dataEntryId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<AudioGraphPoint> getGraphData() {
        return graphData;
    }

    public void setGraphData(List<AudioGraphPoint> graphData) {
        this.graphData = graphData;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
}
