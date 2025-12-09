package com.example.mvvmwechat.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mvvmwechat.data.Outfit;
import com.example.mvvmwechat.repository.OutfitRepository;

import java.util.List;

/**
 * OutfitViewModel - 保持兼容你的现有接口，同时增加 loading/error LiveData
 *
 * 主要职责：
 *  - 暴露本地 outfits 的 LiveData (allOutfits)
 *  - 在构造时触发 repo.refreshFromNetwork()（保持原有行为）
 *  - 暴露 search/addFavorite/refresh/getGenderSetting 等方法（与原来一致）
 *  - 提供 loading/error 以便 UI 能显示加载与错误状态
 */
public class OutfitViewModel extends AndroidViewModel {
    private static final String TAG = "OutfitViewModel";

    private final OutfitRepository repo;

    // 缓存 LiveData，防止 View 每次调用 loadAll 都重新创建新的实例
    private final LiveData<List<Outfit>> allOutfits;

    // 状态 LiveData（可被 Fragment 观察）
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    // 直接使用 Repository 暴露的性别设置 LiveData（如果 repo 提供）
    private final LiveData<String> genderSetting;

    public OutfitViewModel(@NonNull Application application) {
        super(application);
        repo = new OutfitRepository(application.getApplicationContext());

        // 1. 观察本地数据库
        allOutfits = repo.loadAllLocal();

        // 2. 读取性别设置（如果 repo 没有实现该方法，这里会编译报错，请实现 repo.getGenderSetting()）
        LiveData<String> gs;
        try {
            gs = repo.getGenderSetting();
        } catch (Exception e) {
            Log.w(TAG, "repo.getGenderSetting() not available or failed; returning empty LiveData", e);
            gs = new MutableLiveData<>((String) null);
        }
        genderSetting = gs;

        // 3. ⚠️ 关键修改：初始化时立即触发网络请求（保持你写过的行为）
        //    我们不会在这里设置 loading=true/false，因为 repo.refreshFromNetwork() 的实现可能是异步且没有回调。
        //    如果你希望 loading 能准确反映网络刷新状态，请让 repo.refreshFromNetwork() 提供回调或返回 LiveData，
        //    然后在这里绑定回调/观察以更新 loading/error。
        try {
            repo.refreshFromNetwork();
        } catch (Exception e) {
            Log.e(TAG, "refreshFromNetwork failed on init", e);
            error.postValue(e.getMessage());
        }
    }

    /**
     * 返回本地 outfits 的 LiveData（Room 数据库变化时会自动通知）
     */
    public LiveData<List<Outfit>> loadAll() {
        return allOutfits;
    }

    /**
     * 搜索（如果 repo.search 已实现）
     */
    public LiveData<List<Outfit>> search(String keyword) {
        return repo.search(keyword);
    }

    /**
     * 将指定 outfit 添加为收藏（异步由 repo 管理）
     */
    public void addFavorite(long outfitId) {
        repo.addFavorite(outfitId);
    }

    /**
     * 手动触发刷新：会调用 repo.refreshFromNetwork()
     * 如果你希望 UI 能显示刷新过程，请在 Repository 中增加回调或 LiveData 支持，然后在这里更新 loading/error。
     */
    public void refresh() {
        try {
            // 如果需要在 UI 上显示 loading，可以在调用前设置：
            // loading.postValue(true);
            repo.refreshFromNetwork();
            // 当有真正的回调/结果时再设置 loading=false / error
        } catch (Exception e) {
            Log.e(TAG, "refresh failed", e);
            error.postValue(e.getMessage());
            // loading.postValue(false);
        }
    }

    /**
     * 返回用于 UI 显示的 loading/error 状态
     */
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * 返回性别设置 LiveData（由 repo 提供）
     */
    public LiveData<String> getGenderSetting() {
        return genderSetting;
    }

    /**
     * 可选扩展：
     * 如果你在 OutfitRepository 中添加了 refreshFromNetwork(Callback) 或返回 LiveData<Boolean> 的方法，
     * 可在这里把 loading/error 绑定到 Repository 的回调上，使 UI 能够实时反映刷新进度。
     *
     * 例如（伪代码）：
     *
     * repo.refreshFromNetwork(new Repo.Callback() {
     *     onStart() { loading.postValue(true); }
     *     onSuccess() { loading.postValue(false); error.postValue(null); }
     *     onError(msg) { loading.postValue(false); error.postValue(msg); }
     * });
     */
}
