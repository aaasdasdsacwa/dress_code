package com.example.mvvmwechat.repository;

import android.content.Context;
import android.util.Log; // 新增：用于打印日志

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mvvmwechat.data.AppDatabase;
import com.example.mvvmwechat.data.Favorite;
import com.example.mvvmwechat.data.Outfit;
import com.example.mvvmwechat.network.RetrofitClient; // ⚠️新增：确保你有这个类

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call; // 新增
import retrofit2.Response; // 新增

/**
 * OutfitRepository
 * - 负责和 Room（AppDatabase）交互
 * - 负责从网络 (Python Backend) 拉取数据
 * - 提供线程池执行数据库操作
 */
public class OutfitRepository {
    private final AppDatabase db;
    private final ExecutorService executor;

    public OutfitRepository(Context context) {
        db = AppDatabase.getInstance(context.getApplicationContext());
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 核心新功能：从 Python 后端拉取数据并同步到本地数据库
     * 逻辑：Network -> List<Outfit> -> Delete Local DB -> Insert New Data
     */
    public void refreshFromNetwork() {
        executor.execute(() -> {
            try {
                // 1. 发起网络请求 (同步方式 execute)
                Call<List<Outfit>> call = RetrofitClient.getInstance().getApi().getAllOutfits();
                Response<List<Outfit>> response = call.execute();

                // 2. 判断请求是否成功
                if (response.isSuccessful() && response.body() != null) {
                    List<Outfit> networkData = response.body();

                    // 3. 写入本地数据库 (开启事务以保证数据完整性)
                    db.runInTransaction(() -> {
                        // ⚠️ 注意：需要在 OutfitDao 里实现 deleteAll() 和 insertAll()
                        db.outfitDao().deleteAll(); // 清空旧缓存
                        db.outfitDao().insertAll(networkData); // 写入新数据
                    });

                    Log.d("OutfitRepo", "网络同步成功，更新了 " + networkData.size() + " 条数据");
                } else {
                    Log.e("OutfitRepo", "网络请求失败，错误码: " + response.code());
                }

            } catch (IOException e) {
                // 网络不通（没联网，或连不上服务器）
                Log.e("OutfitRepo", "网络连接错误", e);
                e.printStackTrace();
            } catch (Exception e) {
                Log.e("OutfitRepo", "数据同步异常", e);
            }
        });
    }

    /**
     * 从本地数据库异步加载所有 Outfit，返回 LiveData 供 UI 观察。
     * UI 会自动感知 refreshFromNetwork 带来的数据库变化
     */
    public LiveData<List<Outfit>> loadAllLocal() {
        final MutableLiveData<List<Outfit>> live = new MutableLiveData<>();
        executor.execute(() -> {
            List<Outfit> list = db.outfitDao().getAll();
            if (list == null) list = new ArrayList<>();
            live.postValue(list);
        });
        return live;
    }

    /**
     * 关键字搜索（title 或 style 匹配）
     */
    public LiveData<List<Outfit>> search(String keyword) {
        final MutableLiveData<List<Outfit>> live = new MutableLiveData<>();
        final String kw = "%" + keyword + "%";

        executor.execute(() -> {
            List<Outfit> list = db.outfitDao().search(kw);
            if (list == null) list = new ArrayList<>();
            live.postValue(list);
        });

        return live;
    }

    /**
     * 将指定 outfit 添加为收藏（异步）
     */
    public void addFavorite(final long outfitId) {
        executor.execute(() -> {
            Favorite f = new Favorite();
            f.outfitId = outfitId;
            f.addedAt = String.valueOf(System.currentTimeMillis());
            db.favoriteDao().insert(f);
        });
    }

    public LiveData<List<Favorite>> getFavorites() {
        final MutableLiveData<List<Favorite>> live = new MutableLiveData<>();
        executor.execute(() -> {
            List<Favorite> list = db.favoriteDao().getAll();
            if (list == null) list = new ArrayList<>();
            live.postValue(list);
        });
        return live;
    }

    /**
     * 组合筛选 (内存筛选，逻辑保持不变)
     */
    public LiveData<List<Outfit>> filter(final String gender, final String style,
                                         final String season, final String weather,
                                         final String scene, final String keyword) {
        final MutableLiveData<List<Outfit>> live = new MutableLiveData<>();
        executor.execute(() -> {
            // 先拿所有数据
            List<Outfit> all = db.outfitDao().getAll();
            if (all == null) all = new ArrayList<>();

            List<Outfit> out = new ArrayList<>();
            // 处理 null 值，防止空指针
            String g = (gender == null) ? "" : gender.trim().toLowerCase();
            String s = (style == null) ? "" : style.trim().toLowerCase();
            String se = (season == null) ? "" : season.trim().toLowerCase();
            String w = (weather == null) ? "" : weather.trim().toLowerCase();
            String sc = (scene == null) ? "" : scene.trim().toLowerCase();
            String kw = (keyword == null) ? "" : keyword.trim().toLowerCase();

            for (Outfit o : all) {
                boolean ok = true;

                // 筛选逻辑
                if (!g.isEmpty() && !g.equals("all")) {
                    String og = (o.gender == null) ? "" : o.gender.toLowerCase();
                    if (!og.equals("unisex") && !og.equals(g)) ok = false;
                }
                if (ok && !s.isEmpty()) {
                    String os = (o.style == null) ? "" : o.style.toLowerCase();
                    if (!os.contains(s)) ok = false;
                }
                if (ok && !se.isEmpty()) {
                    String ose = (o.season == null) ? "" : o.season.toLowerCase();
                    if (!ose.contains(se)) ok = false;
                }
                if (ok && !w.isEmpty()) {
                    String ow = (o.weather == null) ? "" : o.weather.toLowerCase();
                    if (!ow.contains(w)) ok = false;
                }
                if (ok && !sc.isEmpty()) {
                    String osc = (o.scene == null) ? "" : o.scene.toLowerCase();
                    if (!osc.contains(sc)) ok = false;
                }
                if (ok && !kw.isEmpty()) {
                    String title = (o.title == null) ? "" : o.title.toLowerCase();
                    String styleField = (o.style == null) ? "" : o.style.toLowerCase();
                    if (!title.contains(kw) && !styleField.contains(kw)) ok = false;
                }

                if (ok) out.add(o);
            }

            live.postValue(out);
        });
        return live;
    }

    // 假数据填充：有了网络后端后，这个方法基本不需要了，
    // 但可以保留作为网络断开时的测试备用
    public void seedSampleDataIfEmpty() {
        executor.execute(() -> {
            List<Outfit> list = db.outfitDao().getAll();
            if (list != null && list.size() > 0) return;

            // ... 这里是你原来的测试数据 ...
            // 如果后端连通了，建议不运行这个，否则会和后端数据冲突
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
    public LiveData<String> getGenderSetting() {
        final MutableLiveData<String> live = new MutableLiveData<>();
        executor.execute(() -> {
            // 从 Setting 表读取 key="gender" 的值
            // 注意：这里假设你 SettingDao 里的 get 方法返回 String
            String g = db.settingDao().get("gender");

            // 如果没设置过(null)，默认返回 "all"
            if (g == null) g = "all";

            live.postValue(g);
        });
        return live;
    }
    /**
     * 返回当前收藏的 Outfit（从 favorites 表关联 outfits）
     */
    public LiveData<List<Outfit>> getFavoriteOutfits() {
        final MutableLiveData<List<Outfit>> live = new MutableLiveData<>();
        executor.execute(() -> {
            List<Favorite> favs = db.favoriteDao().getAll();
            List<Outfit> all = db.outfitDao().getAll();
            List<Outfit> results = new ArrayList<>();
            if (favs != null && all != null) {
                // 建立 id -> Outfit map
                java.util.HashMap<Long, Outfit> map = new java.util.HashMap<>();
                for (Outfit o : all) {
                    map.put(o.id, o);
                }
                for (Favorite f : favs) {
                    Outfit o = map.get(f.outfitId);
                    if (o != null) results.add(o);
                }
            }
            live.postValue(results);
        });
        return live;
    }
}