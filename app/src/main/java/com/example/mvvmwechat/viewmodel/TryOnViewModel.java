package com.example.mvvmwechat.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mvvmwechat.network.AiApi;

import java.io.File;

/**
 * TryOnViewModel - 负责调用 AiApi 并把结果暴露为 LiveData
 */
public class TryOnViewModel extends AndroidViewModel {
    private final MutableLiveData<String> resultImageUrl = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public TryOnViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<String> getResultImageUrl() {
        return resultImageUrl;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * 调用 AI 换装（在 AiApi 内部使用 OkHttp 异步）
     * apiKey 传入 BuildConfig.AI_KEY（或 null）
     */
    public void tryOn(File userImage, File outfitImage, String apiKey) {
        if (userImage == null || outfitImage == null) {
            error.postValue("图片不存在");
            return;
        }

        loading.postValue(true);
        error.postValue(null);

        AiApi.tryOnReturnUrl(getApplication(), userImage, outfitImage, apiKey, new AiApi.UrlCallback() {
            @Override
            public void onSuccess(String url) {
                loading.postValue(false);
                resultImageUrl.postValue(url);
            }

            @Override
            public void onError(Exception e) {
                loading.postValue(false);
                error.postValue(e == null ? "未知错误" : e.getMessage());
            }
        });
    }
}
