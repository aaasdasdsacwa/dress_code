package com.example.mvvmwechat.data;

import androidx.room.Entity;
import androidx.room.Ignore; // ⚠️ 必须导入这个
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "outfits")
public class Outfit {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String gender;
    public String style;
    public String season;
    public String weather;
    public String scene;

    // 对应 Python 返回的 JSON 中的 "image_url"
    @SerializedName("image_url")
    public String imagePath;

    // --- 构造函数 1：给 Room 和 Retrofit/Gson 使用的 ---
    public Outfit() {
    }

    // --- 构造函数 2：这是你自己写测试数据用的 ---
    // ⚠️ 加上 @Ignore，告诉 Room："别用这个，这不是给你用的"
    @Ignore
    public Outfit(String title, String gender, String style, String season, String weather, String scene, String imagePath) {
        this.title = title;
        this.gender = gender;
        this.style = style;
        this.season = season;
        this.weather = weather;
        this.scene = scene;
        this.imagePath = imagePath;
    }
}