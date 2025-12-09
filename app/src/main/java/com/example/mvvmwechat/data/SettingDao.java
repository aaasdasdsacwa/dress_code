package com.example.mvvmwechat.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(Setting s);

    @Query("SELECT value FROM settings WHERE key = :key LIMIT 1")
    String get(String key);
}
