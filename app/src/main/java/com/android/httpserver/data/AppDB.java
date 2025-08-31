package com.android.httpserver.data;

import android.content.Context;

import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.android.httpserver.model.History;

@androidx.room.Database(entities = {History.class}, version = 1)
public abstract class AppDB extends RoomDatabase {
    private static AppDB instance;

    public abstract HistoryDao historyDao();

    public static synchronized AppDB getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDB.class, "history_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
