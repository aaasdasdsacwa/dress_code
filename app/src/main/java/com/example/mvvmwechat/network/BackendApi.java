package com.example.mvvmwechat.network;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
public class BackendApi {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 连接超时 30秒
            .readTimeout(30, TimeUnit.SECONDS)    // 读取超时 30秒
            .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时 30秒
            .build();
    // 注意：原本你的 BASE 结尾有 "/"，调用时请确保路径不要重复 "/"，或者服务器能处理双斜杠
    private static final String BASE = "http://10.0.2.2:8000/";

    public static void postExample(String jsonBody, Callback cb) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        // 保持原样
        Request req = new Request.Builder().url(BASE + "/outfits").post(body).build();
        client.newCall(req).enqueue(cb);
    }

    /**
     * 修改后的 syncGet：支持相对路径（你的后端）和绝对路径（外部API，如天气）
     */
    public static String syncGet(String pathOrUrl) throws IOException {
        String finalUrl;

        // 判断传入的字符串是否以 http 开头（即是否为完整链接）
        if (pathOrUrl.startsWith("http")) {
            // 如果是天气API这种完整链接，直接使用
            finalUrl = pathOrUrl;
        } else {
            // 如果是相对路径（例如 "/outfits"），则拼接你的后端地址
            finalUrl = BASE + pathOrUrl;
        }

        Request req = new Request.Builder().url(finalUrl).get().build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("Unexpected code " + res);
            // 这里的 body().string() 可能为空，如果 API 返回空会报错，建议生产环境加非空判断，但在示例中保持原样即可
            return res.body() != null ? res.body().string() : "";
        }
    }
}