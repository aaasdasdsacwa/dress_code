package com.example.mvvmwechat.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "settings")
public class Setting {
    @PrimaryKey
    @NonNull
    public String key;

    public String value;

    // Room 使用的无参构造函数
    public Setting() {}

    // 便捷构造函数，给应用代码使用；Room 会忽略它
    @Ignore
    public Setting(@NonNull String key, String value) {
        this.key = key;
        this.value = value;
    }
}
