package com.android.httpserver.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.android.httpserver.model.History;

import java.util.List;

@Dao
public interface HistoryDao {

    @Insert
    void insert(History history);

    @Query("SELECT * FROM history ORDER BY id DESC")
    LiveData<List<History>> getAllHistory();

    @Query("SELECT * FROM history WHERE id = :id")
    History getHistoryById(long id);

    @Delete
    void delete(History history);

    @Query("DELETE FROM history")
    void deleteAll();
}
