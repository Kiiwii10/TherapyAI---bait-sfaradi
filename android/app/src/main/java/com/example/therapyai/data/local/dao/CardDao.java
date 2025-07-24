package com.example.therapyai.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.therapyai.data.local.models.CardItem;

import java.util.List;

@Dao
public interface CardDao {
    @Query("SELECT * FROM card_items ORDER BY position ASC")
    List<CardItem> getAllCards();

    @Query("SELECT * FROM card_items WHERE id = :id LIMIT 1")
    CardItem getCardById(long id);

    @Query("SELECT * FROM card_items WHERE " +
            "',' || categories || ',' LIKE '%,' || :categoryName || ',%' ")
    List<CardItem> getCardsByCategory(String categoryName);

    @Query("SELECT * FROM card_items WHERE title = :title LIMIT 1")
    CardItem getCardByTitle(String title);

    @Insert
    void insertCards(CardItem... cardItems);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCard(CardItem cardItem);

    @Update
    void updateCard(CardItem card);

    @Update
    void updateCards(List<CardItem> cards);

    @Delete
    void deleteCards(List<CardItem> cards);

    @Delete
    void deleteCard(CardItem card);

    @Query("SELECT IFNULL(MAX(position), -1) FROM card_items")
    int getMaxPosition();

}

