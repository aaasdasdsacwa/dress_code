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
 * 保持并兼容原有功能（tryOnReturnUrl），并新增：
 *  - tryOnWithOutfitUrl(...)：当 outfit 为远程 URL 时使用（上传用户图 + outfit_url 字段）
 *  - 更好的错误/HTML 识别与 log 输出，便于排查返回非 JSON 的情况
 *
 * 注意：默认使用 AI_BASE 常量作为 endpoint（原来你用的是 https://www.dmxapi.cn/v1），
 *       如需修改请替换 AI_BASE 或改写调用。
 */
public class AiApi {
    private static final String TAG = "AiApi";
    private static final OkHttpClient client = new OkHttpClient();

    // 默认 endpoint（保留你原来的地址）
    private static final String AI_BASE = "https://tryon-api.com";

    public interface UrlCallback {
        void onSuccess(String url); // 返回远程 url 或 本地 file:// uri
        void onError(Exception e);
    }

    // -------------------------
    // 原有方法：上传 user + outfit 两张文件（保持原样，但加了日志与 HTML 判别）
    // -------------------------
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
                try {
                    if (!response.isSuccessful()) {
                        final IOException ex = new IOException("HTTP " + response.code() + " " + response.message());
                        Log.w(TAG, "tryOnReturnUrl response not successful: " + response.code());
                        main.post(() -> cb.onError(ex));
                        return;
                    }

                    String ct = "";
                    if (response.body() != null && response.body().contentType() != null) {
                        ct = response.body().contentType().toString().toLowerCase();
                    }
                    Log.d(TAG, "tryOnReturnUrl content-type: " + ct);

                    // JSON 情形
                    if (ct.contains("application/json") || ct.contains("text/json") || ct.contains("application/ld+json") || ct.contains("text/plain")) {
                        String bodyStr = response.body() != null ? response.body().string() : "";
                        // 如果 body 看起来像 HTML，优先返回可读片段帮助诊断
                        if (looksLikeHtml(bodyStr)) {
                            final Exception ex = new Exception("Unexpected HTML response from server: " + snippet(bodyStr, 800));
                            Log.w(TAG, "tryOnReturnUrl got HTML in JSON branch");
                            main.post(() -> cb.onError(ex));
                            return;
                        }

                        String found = parseUrlFromJson(bodyStr);
                        if (found != null) {
                            final String urlFinal = found;
                            Log.d(TAG, "tryOnReturnUrl parsed url: " + urlFinal);
                            main.post(() -> cb.onSuccess(urlFinal));
                            return;
                        } else {
                            final Exception ex = new Exception("No url field found in JSON response: " + snippet(bodyStr, 800));
                            Log.w(TAG, "tryOnReturnUrl JSON did not contain url");
                            main.post(() -> cb.onError(ex));
                            return;
                        }
                    }

                    // 图片二进制
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
                        } catch (Exception e) {
                            final Exception ex = new Exception("Failed to save binary response: " + e.getMessage(), e);
                            Log.e(TAG, "tryOnReturnUrl save binary failed", e);
                            main.post(() -> cb.onError(ex));
                            return;
                        }
                        final String localUri = Uri.fromFile(out).toString();
                        Log.d(TAG, "tryOnReturnUrl saved binary to: " + localUri);
                        main.post(() -> cb.onSuccess(localUri));
                        return;
                    }

                    // 兜底：按字符串解析（有些服务 Content-Type 不准确）
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    if (looksLikeHtml(bodyStr)) {
                        final Exception ex = new Exception("Unexpected response format (HTML). Body snippet: " + snippet(bodyStr, 800));
                        Log.w(TAG, "tryOnReturnUrl got HTML in fallback branch");
                        main.post(() -> cb.onError(ex));
                        return;
                    }
                    String found = parseUrlFromJson(bodyStr);
                    if (found != null) {
                        final String urlFinal = found;
                        Log.d(TAG, "tryOnReturnUrl parsed url in fallback: " + urlFinal);
                        main.post(() -> cb.onSuccess(urlFinal));
                    } else {
                        final Exception ex = new Exception("Unexpected response format, body: " + snippet(bodyStr, 800));
                        Log.w(TAG, "tryOnReturnUrl unexpected response");
                        main.post(() -> cb.onError(ex));
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "tryOnReturnUrl processing error", ex);
                    main.post(() -> cb.onError(ex));
                } finally {
                    if (response.body() != null) response.close();
                }
            }
        });
    }

    // -------------------------
    // 新增方法：上传 userImage + outfitImageUrl（远程 URL），后端负责抓取 outfit 图
    // -------------------------
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
                // 可添加其它可选字段，如 model, preserve_color, prompt 等
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
                try {
                    if (!response.isSuccessful()) {
                        final IOException ex = new IOException("HTTP " + response.code() + " " + response.message());
                        Log.w(TAG, "tryOnWithOutfitUrl response not successful: " + response.code());
                        String bodyStr = response.body() != null ? response.body().string() : null;
                        Log.w(TAG, "tryOnWithOutfitUrl response body snippet: " + snippet(bodyStr, 800));
                        main.post(() -> cb.onError(ex));
                        return;
                    }

                    String ct = "";
                    if (response.body() != null && response.body().contentType() != null) {
                        ct = response.body().contentType().toString().toLowerCase();
                    }
                    Log.d(TAG, "tryOnWithOutfitUrl content-type: " + ct);

                    // JSON
                    if (ct.contains("application/json") || ct.contains("text/json") || ct.contains("application/ld+json") || ct.contains("text/plain")) {
                        String bodyStr = response.body() != null ? response.body().string() : "";
                        if (looksLikeHtml(bodyStr)) {
                            final Exception ex = new Exception("Unexpected HTML response from server: " + snippet(bodyStr, 800));
                            Log.w(TAG, "tryOnWithOutfitUrl got HTML in JSON branch");
                            main.post(() -> cb.onError(ex));
                            return;
                        }
                        String found = parseUrlFromJson(bodyStr);
                        if (found != null) {
                            final String urlFinal = found;
                            Log.d(TAG, "tryOnWithOutfitUrl parsed url: " + urlFinal);
                            main.post(() -> cb.onSuccess(urlFinal));
                            return;
                        } else {
                            final Exception ex = new Exception("No url field found in JSON response: " + snippet(bodyStr, 800));
                            Log.w(TAG, "tryOnWithOutfitUrl JSON did not contain url");
                            main.post(() -> cb.onError(ex));
                            return;
                        }
                    }

                    // 图片二进制
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
                        } catch (Exception e) {
                            final Exception ex = new Exception("Failed to save binary response: " + e.getMessage(), e);
                            Log.e(TAG, "tryOnWithOutfitUrl save binary failed", e);
                            main.post(() -> cb.onError(ex));
                            return;
                        }
                        final String localUri = Uri.fromFile(out).toString();
                        Log.d(TAG, "tryOnWithOutfitUrl saved binary to: " + localUri);
                        main.post(() -> cb.onSuccess(localUri));
                        return;
                    }

                    // 兜底
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    if (looksLikeHtml(bodyStr)) {
                        final Exception ex = new Exception("Unexpected response format (HTML). Body snippet: " + snippet(bodyStr, 800));
                        Log.w(TAG, "tryOnWithOutfitUrl got HTML in fallback branch");
                        main.post(() -> cb.onError(ex));
                        return;
                    }
                    String found = parseUrlFromJson(bodyStr);
                    if (found != null) {
                        final String urlFinal = found;
                        Log.d(TAG, "tryOnWithOutfitUrl parsed url in fallback: " + urlFinal);
                        main.post(() -> cb.onSuccess(urlFinal));
                    } else {
                        final Exception ex = new Exception("Unexpected response format, body: " + snippet(bodyStr, 800));
                        Log.w(TAG, "tryOnWithOutfitUrl unexpected response");
                        main.post(() -> cb.onError(ex));
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "tryOnWithOutfitUrl processing error", ex);
                    main.post(() -> cb.onError(ex));
                } finally {
                    if (response.body() != null) response.close();
                }
            }
        });
    }

    // -------------------------
    // JSON 解析工具（沿用你原来的 parseUrlFromJson）
    // -------------------------
    /**
     * 尝试从 JSON 字符串中提取可能的图片 URL（尽量兼容多种后端返回格式）
     * 支持键（优先顺序）：result_url, url, data.result, data.url, data[0].url, outputs[0].url, result[0].url
     */
    private static String parseUrlFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject jo = new JSONObject(jsonStr);

            // 直接常见字段
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

            // data -> object or array
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

            // outputs -> array
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

            // 有时返回嵌套 result 字段
            if (jo.has("result") && jo.opt("result") instanceof JSONObject) {
                JSONObject rjo = jo.optJSONObject("result");
                if (rjo != null && rjo.has("url")) {
                    String s = rjo.optString("url", null);
                    if (looksLikeUrl(s)) return s;
                }
            }

        } catch (JSONException e) {
            // ignore and return null
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
