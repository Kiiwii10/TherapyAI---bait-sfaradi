package com.example.therapyai.data.local.models;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Objects;

public class NoteCard implements Serializable {
    private static final long serialVersionUID = 1L;

    private String title;
    private String timestamp;
    private String content;
    private int index;

    public NoteCard(String title, String timestamp, String content, int index) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getIndex() {
        return index;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String stringHashCode() {
        return title + "," + timestamp + "," + content + "," + index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoteCard noteCard = (NoteCard) o;
        return index == noteCard.index && Objects.equals(title, noteCard.title) && Objects.equals(timestamp, noteCard.timestamp) && Objects.equals(content, noteCard.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, timestamp, content, index);
    }

    @Override
    public String toString() {
        return "NoteCard{" +
                "title='" + title + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", content='" + content + '\'' +
                ", index=" + index +
                '}';
    }

}