package com.example.mvvmwechat.data;


import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity(tableName = "favorites")
public class Favorite {
    @PrimaryKey(autoGenerate = true)
    public long id;


    public long outfitId; // 关联 Outfit.id
    public String addedAt; // 时间字符串
}
