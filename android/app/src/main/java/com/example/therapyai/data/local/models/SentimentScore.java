package com.example.therapyai.data.local.models;

import java.util.Objects;

public class SentimentScore {
    private float positive;
    private float neutral;
    private float negative;

    public SentimentScore(float positive, float neutral, float negative) {
        this.positive = positive;
        this.neutral = neutral;
        this.negative = negative;
    }

    public float getPositive() { return positive; }
    public float getNeutral() { return neutral; }
    public float getNegative() { return negative; }
    public void setPositive(float positive) { this.positive = positive; }
    public void setNeutral(float neutral) { this.neutral = neutral; }
    public void setNegative(float negative) { this.negative = negative; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SentimentScore that = (SentimentScore) o;
        return Float.compare(that.positive, positive) == 0 && Float.compare(that.neutral, neutral) == 0 && Float.compare(that.negative, negative) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(positive, neutral, negative);
    }
}