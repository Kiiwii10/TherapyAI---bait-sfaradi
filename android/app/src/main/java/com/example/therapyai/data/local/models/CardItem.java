package com.example.therapyai.data.local.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import androidx.room.TypeConverters;
import com.example.therapyai.data.local.db.converters.CategorySetConverter;
import java.util.HashSet;
import java.util.Set;

@Entity(tableName = "card_items")
@TypeConverters({CategorySetConverter.class})
public class CardItem implements Serializable {
    @Ignore
    private static final long serialVersionUID = 1L;

    @PrimaryKey(autoGenerate = true)
    private long id; // unique ID, idk if needed
    private String title;
    private String description;
    private String sessionNotes;
    private Set<String> categories;
    private String type;
    private boolean isSelected;
    private int position;

    public CardItem() {
        this.categories = new HashSet<>();
        this.isSelected = false;
        // Initialize other fields to defaults if necessary
        this.title = "";
        this.description = "";
        this.sessionNotes = "";
        this.type = "Default Audio"; // Default type
        this.position = -1; // Default position
    }

    @Ignore
    public CardItem(String title, String description) {
        this();
        this.title = title;
        this.description = description;
    }

    @Ignore
    public CardItem(String title, String description, String sessionNotes) {
        this();
        this.title = title;
        this.description = description;
        this.sessionNotes = sessionNotes;
    }

    @Ignore
    public CardItem(String title,
                    String description,
                    Set<String> categories,
                    String type) {
        this.title = title;
        this.description = description;
        this.categories = categories != null ? new HashSet<>(categories) : new HashSet<>();
        this.type = type;
        this.isSelected = false;
        this.position = -1;
    }



    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSessionNotes() { return sessionNotes; }
    public void setSessionNotes(String sessionNotes) { this.sessionNotes = sessionNotes; }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public Set<String> getCategories() {
        return categories == null ? new HashSet<>() : categories;
    }
    public void setCategories(Set<String> category) {
        this.categories = category;
    }

    public void addCategory(String category) {
        if (this.categories == null) {
            this.categories = new HashSet<>();
        }
        this.categories.add(category);
    }

    public void removeCategory(String category) {
        if (this.categories != null) {
            this.categories.remove(category);
        }
    }



    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

}
