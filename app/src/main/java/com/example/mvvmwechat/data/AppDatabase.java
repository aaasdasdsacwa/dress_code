package com.example.mvvmwechat.data;


import android.content.Context;


import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


@Database(entities = {User.class, Outfit.class, Favorite.class, UploadedImage.class, Setting.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract OutfitDao outfitDao();
    public abstract FavoriteDao favoriteDao();
    public abstract UploadedImageDao uploadedImageDao();
    public abstract SettingDao settingDao();


    private static volatile AppDatabase INSTANCE;


    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "mvvm_outfit_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}