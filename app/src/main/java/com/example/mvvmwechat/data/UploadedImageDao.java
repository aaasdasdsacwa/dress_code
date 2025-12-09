package com.example.mvvmwechat.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UploadedImageDao {
    @Insert
    void insert(UploadedImage img);

    @Query("SELECT * FROM uploaded_images ORDER BY id DESC")
    List<UploadedImage> getAll();
}
