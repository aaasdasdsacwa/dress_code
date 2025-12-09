package com.example.mvvmwechat.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy; // ⚠️ 新增导入
import androidx.room.Query;

import java.util.List;

@Dao
public interface OutfitDao {

    // --- 原有方法保持不变 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE) // 建议加上 REPLACE 策略
    void insert(Outfit outfit);

    @Query("SELECT * FROM outfits")
    List<Outfit> getAll();

    // 简单筛选示例
    @Query("SELECT * FROM outfits WHERE (gender = :gender OR :gender = 'all') AND (style = :style OR :style = '')")
    List<Outfit> filter(String gender, String style);

    // 关键字搜索
    @Query("SELECT * FROM outfits WHERE title LIKE :keyword OR style LIKE :keyword")
    List<Outfit> search(String keyword);

    // --- ⚠️ 新增以下两个方法，Repository 里的 refreshFromNetwork 需要它们 ---

    /**
     * 批量插入数据（用于网络同步）
     * 如果 ID 冲突，直接覆盖旧数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Outfit> outfits);

    /**
     * 清空整个表（用于刷新缓存）
     */
    @Query("DELETE FROM outfits")
    void deleteAll();
}