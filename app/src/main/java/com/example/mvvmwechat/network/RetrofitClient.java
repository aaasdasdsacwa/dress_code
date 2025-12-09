package com.example.mvvmwechat.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // 和你 BackendApi 里的地址保持一致
    private static final String BASE_URL = "http://10.0.2.2:8000/";

    private static RetrofitClient instance;
    private final OutfitApiService apiService;

    private RetrofitClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()) // 自动把 JSON 转成 List<Outfit>
                .build();

        apiService = retrofit.create(OutfitApiService.class);
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    public OutfitApiService getApi() {
        return apiService;
    }
}