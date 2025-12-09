package com.example.mvvmwechat.network;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AiApi - 通用的 tryOn 上传工具（增强版）
 *
 * 包含：
 * 1. 同步等待模式 (旧功能)：tryOnReturnUrl, tryOnWithOutfitUrl
 * 2. 异步轮询模式 (新功能)：tryOnAsync (专门适配 tryon-api.com 的 job 机制)
 */
public class AiApi {
    private static final String TAG = "AiApi";

    // 基础 Client 配置
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    // 默认 endpoint（保留你原来的地址）
    private static final String AI_BASE = "https://tryon-api.com";

    // 新增：针对 tryon-api.com 异步任务的专用 API 地址
    private static final String ASYNC_API_HOST = "https://tryon-api.com";
    private static final String ENDPOINT_SUBMIT = ASYNC_API_HOST + "/api/v1/tryon";
    private static final String ENDPOINT_STATUS = ASYNC_API_HOST + "/api/v1/tryon/status/";

    public interface UrlCallback {
        void onSuccess(String url); // 返回远程 url 或 本地 file:// uri
        void onError(Exception e);
    }

    // ============================================================================================
    //  SECTION 1: 原有功能 (保持不变)
    //  适用于支持直接返回结果的 API，或旧版接口
    // ============================================================================================

    public static void tryOnReturnUrl(Context ctx, File userImageFile, File outfitImageFile, String apiKey, UrlCallback cb) {
        Handler main = new Handler(Looper.getMainLooper());

        if (userImageFile == null || outfitImageFile == null) {
            main.post(() -> cb.onError(new IllegalArgumentException("userImageFile or outfitImageFile is null")));
            return;
        }

        Log.d(TAG, "tryOnReturnUrl -> AI_BASE=" + AI_BASE + " userFile=" + userImageFile.getAbsolutePath()
                + " outfitFile=" + outfitImageFile.getAbsolutePath());

        MediaType imgType = MediaType.get("image/*");
        RequestBody userBody = RequestBody.create(userImageFile, imgType);
        RequestBody outfitBody = RequestBody.create(outfitImageFile, imgType);

        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_image", userImageFile.getName(), userBody)
                .addFormDataPart("outfit_image", outfitImageFile.getName(), outfitBody)
                // 兼容原来示例中带的 preserve_color
                .addFormDataPart("preserve_color", "true")
                .build();

        Request.Builder rb = new Request.Builder()
                .url(AI_BASE)
                .post(body);

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            rb.addHeader("Authorization", "Bearer " + apiKey.trim());
        }

        Request req = rb.build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "tryOnReturnUrl HTTP failure: " + e.getMessage(), e);
                main.post(() -> cb.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                handleSyncResponse(ctx, response, main, cb, "tryOnReturnUrl");
            }
        });
    }

    public static void tryOnWithOutfitUrl(Context ctx, File userImageFile, String outfitImageUrl, String apiKey, UrlCallback cb) {
        Handler main = new Handler(Looper.getMainLooper());

        if (userImageFile == null || outfitImageUrl == null || outfitImageUrl.trim().isEmpty()) {
            main.post(() -> cb.onError(new IllegalArgumentException("userImageFile or outfitImageUrl is null/empty")));
            return;
        }

        Log.d(TAG, "tryOnWithOutfitUrl -> AI_BASE=" + AI_BASE + " outfitImageUrl=" + outfitImageUrl);

        MediaType imgType = MediaType.get("image/*");
        RequestBody userBody = RequestBody.create(userImageFile, imgType);

        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("user_image", userImageFile.getName(), userBody)
                .addFormDataPart("outfit_image_url", outfitImageUrl)
                .addFormDataPart("preserve_color", "true")
                .build();

        Request.Builder rb = new Request.Builder()
                .url(AI_BASE)
                .post(body);

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            rb.addHeader("Authorization", "Bearer " + apiKey.trim());
        }

        Request req = rb.build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "tryOnWithOutfitUrl HTTP failure: " + e.getMessage(), e);
                main.post(() -> cb.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                handleSyncResponse(ctx, response, main, cb, "tryOnWithOutfitUrl");
            }
        });
    }

    // ============================================================================================
    //  SECTION 2: 新增功能 - 异步轮询 (针对 tryon-api.com)
    //  流程：提交图片 -> 获取 jobId -> 轮询状态 -> 获取最终 URL
    // ============================================================================================

    /**
     * 新增：专门适配 tryon-api.com 的异步方法
     * 参数名变更为 person_images 和 garment_images
     */
    public static void tryOnAsync(Context ctx, File userFile, File outfitFile, String apiKey, UrlCallback cb) {
        Handler main = new Handler(Looper.getMainLooper());

        if (userFile == null || outfitFile == null) {
            main.post(() -> cb.onError(new IllegalArgumentException("Images cannot be null for async try-on")));
            return;
        }

        Log.d(TAG, "tryOnAsync -> Submitting to " + ENDPOINT_SUBMIT);

        // 1. 准备请求体 (注意参数名根据 tryon-api 文档调整)
        MediaType imgType = MediaType.parse("image/png");
        RequestBody userBody = RequestBody.create(userFile, imgType);
        RequestBody outfitBody = RequestBody.create(outfitFile, imgType);

        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("person_images", userFile.getName(), userBody)
                .addFormDataPart("garment_images", outfitFile.getName(), outfitBody)
                // 可选参数，根据需要开启
                // .addFormDataPart("category", "tops")
                .build();

        Request request = new Request.Builder()
                .url(ENDPOINT_SUBMIT)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        // 2. 提交任务
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "tryOnAsync submit failed", e);
                main.post(() -> cb.onError(e));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String respStr = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        main.post(() -> cb.onError(new Exception("Async Submit Failed: " + response.code() + " " + snippet(respStr, 200))));
                        return;
                    }

                    // 解析 jobId
                    // 假设返回格式: { "jobId": "xxxx-xxxx", ... } 或 { "result": "xxxx" }
                    // 根据实际文档，通常直接在根对象里有 id 或 job_id
                    JSONObject json = new JSONObject(respStr);
                    String jobId = json.optString("job_id"); // 常见字段名1
                    if (jobId.isEmpty()) jobId = json.optString("jobId"); // 常见字段名2
                    if (jobId.isEmpty()) jobId = json.optString("id");    // 常见字段名3

                    if (jobId.isEmpty()) {
                        main.post(() -> cb.onError(new Exception("No jobId found in response: " + snippet(respStr, 200))));
                        return;
                    }

                    Log.d(TAG, "tryOnAsync -> Job Submitted. ID=" + jobId + ". Starting Polling...");

                    // 3. 开始轮询
                    startPolling(jobId, apiKey, main, cb);

                } catch (Exception e) {
                    main.post(() -> cb.onError(e));
                } finally {
                    if (response.body() != null) response.close();
                }
            }
        });
    }

    /**
     * 内部方法：轮询状态
     */
    private static void startPolling(String jobId, String apiKey, Handler main, UrlCallback cb) {
        new Thread(() -> {
            int retryCount = 0;
            int maxRetries = 40; // 约 80秒
            boolean isFinished = false;

            while (retryCount < maxRetries && !isFinished) {
                try {
                    Thread.sleep(2000); // 间隔 2秒
                    retryCount++;

                    String statusUrl = ENDPOINT_STATUS + jobId;
                    Request request = new Request.Builder()
                            .url(statusUrl)
                            .addHeader("Authorization", "Bearer " + apiKey)
                            .get()
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Polling network error: " + response.code());
                            continue;
                        }

                        String respStr = response.body() != null ? response.body().string() : "{}";

                        // 【关键修改 1】打印服务器返回的完整 JSON，排查问题根源
                        Log.d(TAG, "Polling Response (" + jobId + "): " + respStr);

                        JSONObject json = new JSONObject(respStr);
                        String status = json.optString("status").toLowerCase();

                        if ("completed".equals(status) || "success".equals(status)) {
                            isFinished = true;
                            String finalUrl = null;

                            // 【关键修改 2】增强 URL 提取逻辑，支持多种格式

                            // 1. 尝试直接取 output_url
                            if (json.has("output_url")) {
                                finalUrl = json.optString("output_url");
                            }
                            // 2. 尝试取 result (可能是字符串或对象)
                            else if (json.has("result")) {
                                Object res = json.get("result");
                                if (res instanceof JSONObject) {
                                    finalUrl = ((JSONObject) res).optString("renderedImageUrl");
                                    if (finalUrl.isEmpty()) finalUrl = ((JSONObject) res).optString("url");
                                } else if (res instanceof String) {
                                    finalUrl = (String) res;
                                }
                            }
                            // 3. 尝试取 outputs 数组 (tryon-api 常用格式)
                            else if (json.has("outputs")) {
                                JSONArray outs = json.optJSONArray("outputs");
                                if (outs != null && outs.length() > 0) {
                                    Object first = outs.get(0);
                                    if (first instanceof String) {
                                        finalUrl = (String) first;
                                    } else if (first instanceof JSONObject) {
                                        finalUrl = ((JSONObject) first).optString("url");
                                    }
                                }
                            }

                            if (finalUrl != null && !finalUrl.isEmpty() && finalUrl.startsWith("http")) {
                                final String urlResult = finalUrl;
                                Log.d(TAG, "Found Final URL: " + urlResult);
                                main.post(() -> cb.onSuccess(urlResult));
                            } else {
                                Log.e(TAG, "Job completed but JSON parse failed. Body: " + respStr);
                                main.post(() -> cb.onError(new Exception("Job completed but no URL found in response.")));
                            }

                        } else if ("failed".equals(status) || "error".equals(status)) {
                            isFinished = true;
                            // 提取更详细的错误信息
                            String msg = json.optString("message", json.optString("error", "Unknown error"));
                            // 如果是因为内容安全（NSFW）被拦截，通常会有提示
                            Log.e(TAG, "Job Failed. Body: " + respStr);
                            main.post(() -> cb.onError(new Exception("AI Job Failed: " + msg)));
                        }
                        // else: processing... continue
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Polling exception", e);
                    if (retryCount > 5 && e instanceof InterruptedException) {
                        isFinished = true;
                        main.post(() -> cb.onError(e));
                    }
                }
            }

            if (!isFinished) {
                main.post(() -> cb.onError(new Exception("Try-on operation timed out.")));
            }
        }).start();
    }

    // ============================================================================================
    //  SECTION 3: 辅助与工具方法 (保持原有逻辑)
    // ============================================================================================

    /**
     * 抽取了原来在 onResponse 里重复的逻辑，用于处理同步请求
     */
    private static void handleSyncResponse(Context ctx, Response response, Handler main, UrlCallback cb, String methodName) {
        try {
            if (!response.isSuccessful()) {
                final IOException ex = new IOException("HTTP " + response.code() + " " + response.message());
                Log.w(TAG, methodName + " response not successful: " + response.code());
                main.post(() -> cb.onError(ex));
                return;
            }

            String ct = "";
            if (response.body() != null && response.body().contentType() != null) {
                ct = response.body().contentType().toString().toLowerCase();
            }
            Log.d(TAG, methodName + " content-type: " + ct);

            // JSON 情形
            if (ct.contains("application/json") || ct.contains("text/json") || ct.contains("application/ld+json") || ct.contains("text/plain")) {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (looksLikeHtml(bodyStr)) {
                    final Exception ex = new Exception("Unexpected HTML response: " + snippet(bodyStr, 800));
                    main.post(() -> cb.onError(ex));
                    return;
                }

                String found = parseUrlFromJson(bodyStr);
                if (found != null) {
                    final String urlFinal = found;
                    main.post(() -> cb.onSuccess(urlFinal));
                } else {
                    final Exception ex = new Exception("No url field found in JSON: " + snippet(bodyStr, 800));
                    main.post(() -> cb.onError(ex));
                }
                return;
            }

            // 图片二进制情形
            if (ct.startsWith("image/") || ct.equals("") || ct.contains("octet-stream")) {
                File out = new File(ctx.getCacheDir(), "tryon_result_" + System.currentTimeMillis() + ".png");
                try (InputStream in = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.flush();
                }
                final String localUri = Uri.fromFile(out).toString();
                main.post(() -> cb.onSuccess(localUri));
                return;
            }

            // 兜底
            String bodyStr = response.body() != null ? response.body().string() : "";
            if (looksLikeHtml(bodyStr)) {
                main.post(() -> cb.onError(new Exception("Unexpected HTML response in fallback")));
                return;
            }
            String found = parseUrlFromJson(bodyStr);
            if (found != null) {
                main.post(() -> cb.onSuccess(found));
            } else {
                main.post(() -> cb.onError(new Exception("Unexpected response format: " + snippet(bodyStr, 800))));
            }

        } catch (Exception ex) {
            Log.e(TAG, methodName + " processing error", ex);
            main.post(() -> cb.onError(ex));
        } finally {
            if (response.body() != null) response.close();
        }
    }

    // 保持原来的解析逻辑不变
    private static String parseUrlFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject jo = new JSONObject(jsonStr);

            String[] directKeys = new String[]{"result_url", "result", "url", "image_url", "output_url", "output"};
            for (String k : directKeys) {
                if (jo.has(k)) {
                    Object v = jo.opt(k);
                    if (v != null) {
                        String s = v.toString();
                        if (looksLikeUrl(s)) return s;
                    }
                }
            }

            if (jo.has("data")) {
                Object data = jo.get("data");
                if (data instanceof JSONObject) {
                    JSONObject djo = (JSONObject) data;
                    String[] keys = new String[]{"result_url", "url", "image_url", "output_url", "output"};
                    for (String k : keys) {
                        if (djo.has(k)) {
                            String s = djo.optString(k, null);
                            if (looksLikeUrl(s)) return s;
                        }
                    }
                } else if (data instanceof JSONArray) {
                    JSONArray arr = (JSONArray) data;
                    if (arr.length() > 0) {
                        Object first = arr.get(0);
                        if (first instanceof JSONObject) {
                            JSONObject fjo = (JSONObject) first;
                            String[] keys = new String[]{"result_url", "url", "image_url", "output_url", "output"};
                            for (String k : keys) {
                                if (fjo.has(k)) {
                                    String s = fjo.optString(k, null);
                                    if (looksLikeUrl(s)) return s;
                                }
                            }
                        } else {
                            String s = first.toString();
                            if (looksLikeUrl(s)) return s;
                        }
                    }
                }
            }

            if (jo.has("outputs")) {
                Object outs = jo.get("outputs");
                if (outs instanceof JSONArray && ((JSONArray) outs).length() > 0) {
                    Object first = ((JSONArray) outs).get(0);
                    if (first instanceof JSONObject) {
                        JSONObject fjo = (JSONObject) first;
                        if (fjo.has("url")) {
                            String s = fjo.optString("url", null);
                            if (looksLikeUrl(s)) return s;
                        }
                    }
                }
            }

            if (jo.has("result") && jo.opt("result") instanceof JSONObject) {
                JSONObject rjo = jo.optJSONObject("result");
                if (rjo != null && rjo.has("url")) {
                    String s = rjo.optString("url", null);
                    if (looksLikeUrl(s)) return s;
                }
            }

        } catch (JSONException e) {
            Log.w(TAG, "parseUrlFromJson JSONException: " + e.getMessage());
        }
        return null;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        s = s.trim();
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file://") || s.startsWith("content://");
    }

    private static boolean looksLikeHtml(String s) {
        if (s == null) return false;
        String low = s.trim().toLowerCase();
        return low.startsWith("<!doctype") || low.startsWith("<html") || low.contains("<html") || low.contains("<!doctype");
    }

    private static String snippet(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}