package com.example.therapyai.data.local.models;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryItem {
    @PrimaryKey
    @NonNull
    private String name;
    private boolean isSelected;
    private boolean isDefault;
    private int position;

    public CategoryItem(@NonNull String name) {
        this.name = name;
        this.isSelected = false;
        if (name.equals("All")) {
            this.isDefault = true;
            this.position = 1;
        } else if (name.equals("+")) {
            this.isDefault = true;
            this.position = 0;
        }
        else {
            this.isDefault = false;
            this.position = 2;
        }
    }

    @NonNull
    public String getName() {
        return name;
    }
    public void setName(@NonNull String name) {
        this.name = name;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}

