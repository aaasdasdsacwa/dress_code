package com.example.mvvmwechat.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "uploaded_images")
public class UploadedImage {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String path; // 本地文件路径
    public String createdAt;

    // Room 使用的无参构造器
    public UploadedImage() {}

    // 如果你想保留便捷构造器供代码使用，标注为 @Ignore 让 Room 忽略它
    @Ignore
    public UploadedImage(String path, String createdAt) {
        this.path = path;
        this.createdAt = createdAt;
    }
}
