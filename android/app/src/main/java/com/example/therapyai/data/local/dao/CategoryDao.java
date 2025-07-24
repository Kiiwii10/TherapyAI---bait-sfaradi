package com.example.therapyai.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.therapyai.data.local.models.CategoryItem;

import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position")
    List<CategoryItem> getAllCategories();


    @Query("SELECT * FROM categories WHERE name != 'All' AND name != '+' ORDER BY position")
    List<CategoryItem> getNonDefaultCategories();

    @Query("SELECT * FROM categories WHERE name = :name")
    CategoryItem getCategoryByName(String name);

    @Query("SELECT * FROM categories WHERE name = 'All'")
    CategoryItem getDefaultAllCategory();

    @Query("SELECT * FROM categories WHERE name = '+'")
    CategoryItem getDefaultAddCategory();


    @Insert
    void insertAll(CategoryItem... categories);

    @Insert
    void insertCategory(CategoryItem category);

    @Update
    void updateCategory(CategoryItem category);

    @Query("UPDATE categories SET name = :newName WHERE name = :oldName")
    int renameCategoryValues(String oldName, String newName); // Returns num rows affected

    @Update
    void updateAll(List<CategoryItem> categories);

    @Query("DELETE FROM categories WHERE name = :name AND isDefault = 0")
    void deleteByName(String name);

    @Delete
    void deleteCategory(CategoryItem category);


}
