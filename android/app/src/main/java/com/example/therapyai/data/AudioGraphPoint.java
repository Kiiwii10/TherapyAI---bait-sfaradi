package com.example.therapyai.data;

public class AudioGraphPoint {
    private double timestamp;
    private double value;

    public AudioGraphPoint(double timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public double getTimestamp() { return timestamp; }
    public void setTimestamp(double timestamp) { this.timestamp = timestamp; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
