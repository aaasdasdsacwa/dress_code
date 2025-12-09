package com.example.mvvmwechat.data;


import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;


import java.util.List;


@Dao
public interface FavoriteDao {
    @Insert
    void insert(Favorite favorite);


    @Query("SELECT * FROM favorites")
    List<Favorite> getAll();


    @Query("DELETE FROM favorites WHERE outfitId = :outfitId")
    void removeByOutfitId(long outfitId);
}