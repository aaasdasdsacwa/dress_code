package com.example.mvvmwechat.network;

import com.example.mvvmwechat.data.Outfit;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;

public interface OutfitApiService {
    // 对应 Python 后端的 @app.get("/outfits")
    @GET("outfits")
    Call<List<Outfit>> getAllOutfits();
}