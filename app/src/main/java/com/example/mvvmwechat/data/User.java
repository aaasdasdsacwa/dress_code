package com.example.mvvmwechat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String username;

    public String password;

    public User(@NonNull String username, String password) {
        this.username = username;
        this.password = password;
    }
}
