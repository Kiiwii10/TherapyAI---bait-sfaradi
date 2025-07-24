package com.example.therapyai.data.local.db;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.therapyai.data.local.dao.CardDao;
import com.example.therapyai.data.local.dao.CategoryDao;
import com.example.therapyai.data.local.db.converters.CategorySetConverter;
import com.example.therapyai.data.local.models.CardItem;
import com.example.therapyai.data.local.models.CategoryItem;

@Database(entities = {
        CardItem.class,
        CategoryItem.class,
        }, version = 3,
        exportSchema = false)
@TypeConverters({CategorySetConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract CardDao cardDao();
    public abstract CategoryDao categoryDao();

    private static volatile AppDatabase INSTANCE;


    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i("DB_MIGRATION", "Running Migration from V1 to V2 for CardItem category Set.");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Log.i("DB_MIGRATION", "Running Migration from V2 to V3 for CardItem category Set.");
            database.execSQL("ALTER TABLE card_items ADD COLUMN sessionNotes TEXT");
            Log.i("DB_MIGRATION", "Migration V2 to V3 completed successfully.");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "therapy_ai_database")
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

}
