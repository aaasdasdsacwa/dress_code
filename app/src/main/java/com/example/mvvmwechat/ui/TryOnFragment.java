package com.example.mvvmwechat.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mvvmwechat.BuildConfig;
import com.example.mvvmwechat.R;
import com.example.mvvmwechat.data.Outfit;
import com.example.mvvmwechat.repository.OutfitRepository;
import com.example.mvvmwechat.viewmodel.TryOnViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TryOnFragment - 使用方案1：对 URL 做正确编码并打印 DNS/地址信息；在 downloadToCache 中使用编码后的 URL
 */
public class TryOnFragment extends Fragment {

    private static final String TAG = "TryOnFragment";
    private static final int REQ_PICK = 1001;

    private ImageView ivUser, ivResult;
    private Button btnPick, btnTryOn;
    private TextView tvNoFavorites;
    private ProgressBar progress;
    private RecyclerView rvFavorites;

    private File pickedFile;
    private Outfit selectedOutfit;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    private TryOnViewModel vm;
    private OutfitRepository outfitRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tryon, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ivUser = view.findViewById(R.id.iv_user);
        ivResult = view.findViewById(R.id.iv_result);
        btnPick = view.findViewById(R.id.btn_pick);
        btnTryOn = view.findViewById(R.id.btn_tryon);
        rvFavorites = view.findViewById(R.id.rv_favorites);
        tvNoFavorites = view.findViewById(R.id.tv_no_favorites);
        progress = view.findViewById(R.id.pb_progress);

        vm = new ViewModelProvider(this).get(TryOnViewModel.class);
        outfitRepository = new OutfitRepository(requireContext());

        // favorites 横向列表
        rvFavorites.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        FavoritesAdapter favAdapter = new FavoritesAdapter(requireContext(), outfit -> {
            selectedOutfit = outfit;
            Toast.makeText(requireContext(), "已选择: " + (outfit.title == null ? "" : outfit.title), Toast.LENGTH_SHORT).show();
        });
        rvFavorites.setAdapter(favAdapter);

        // 观察收藏数据
        outfitRepository.getFavoriteOutfits().observe(getViewLifecycleOwner(), outfits -> {
            if (outfits == null || outfits.isEmpty()) {
                tvNoFavorites.setVisibility(View.VISIBLE);
                rvFavorites.setVisibility(View.GONE);
            } else {
                tvNoFavorites.setVisibility(View.GONE);
                rvFavorites.setVisibility(View.VISIBLE);
                favAdapter.submitList(outfits);
            }
        });

        // 观察 ViewModel (只注册一次)
        vm.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null && isLoading) {
                progress.setVisibility(View.VISIBLE);
                btnTryOn.setEnabled(false);
            } else {
                progress.setVisibility(View.GONE);
                btnTryOn.setEnabled(true);
            }
        });

        vm.getResultImageUrl().observe(getViewLifecycleOwner(), url -> {
            if (url != null && !url.isEmpty()) {
                // url 可能是远程 https://... 或 本地 file://...
                Glide.with(requireContext()).load(url).into(ivResult);
            }
        });

        vm.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) {
                Toast.makeText(requireContext(), "换装失败: " + err, Toast.LENGTH_LONG).show();
            }
        });

        // 选择用户图片
        btnPick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(i, REQ_PICK);
        });

        // 点击开始换装
        btnTryOn.setOnClickListener(v -> {
            if (pickedFile == null) {
                Toast.makeText(requireContext(), "请先选择用户图片", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedOutfit == null) {
                Toast.makeText(requireContext(), "请先从收藏中选择一件穿搭", Toast.LENGTH_SHORT).show();
                return;
            }

            // 显示 loading 状态（ViewModel 也会更新）
            progress.setVisibility(View.VISIBLE);
            btnTryOn.setEnabled(false);

            // 在后台线程准备 outfit 文件（可能需要下载）
            ex.execute(() -> {
                try {
                    // 打印原始与编码后的 URL，并尝试解析主机地址（便于诊断）
                    String rawUrl = selectedOutfit.imagePath;
                    Log.d(TAG, "rawUrl: " + rawUrl);
                    String encoded = encodeUrl(rawUrl);
                    Log.d(TAG, "encodedUrl: " + encoded);
                    try {
                        URL u = new URL(encoded);
                        String host = u.getHost();
                        Log.d(TAG, "host: " + host);
                        try {
                            InetAddress[] addrs = InetAddress.getAllByName(host);
                            for (InetAddress a : addrs) {
                                Log.d(TAG, "addr: " + a.getHostAddress());
                            }
                        } catch (Exception dnsEx) {
                            Log.w(TAG, "DNS lookup failed for host: " + host, dnsEx);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "invalid encoded URL for host resolution: " + encoded, e);
                    }

                    File outfitFile = prepareFileFromPath(requireContext(), selectedOutfit.imagePath);
                    if (outfitFile == null || !outfitFile.exists()) {
                        // 回到主线程安全更新 UI（并检查 Fragment 是否 attach）
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progress.setVisibility(View.GONE);
                            btnTryOn.setEnabled(true);
                            Toast.makeText(getContext(), "无法准备所选穿搭图片（网络或路径问题）", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    // 成功准备好文件后调用 ViewModel（传入 BuildConfig.AI_KEY）
                    vm.tryOn(pickedFile, outfitFile, BuildConfig.AI_KEY);

                } catch (Exception e) {
                    Log.e(TAG, "prepare outfit failed", e);
                    final String msg = e.getMessage() == null ? "准备图片失败" : e.getMessage();
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        progress.setVisibility(View.GONE);
                        btnTryOn.setEnabled(true);
                        Toast.makeText(getContext(), "准备图片失败: " + msg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    // 处理选图结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
                ivUser.setImageBitmap(bmp);
                File f = new File(requireContext().getCacheDir(), "picked_user.png");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                }
                pickedFile = f;
            } catch (Exception e) {
                Log.e(TAG, "select image failed", e);
                Toast.makeText(requireContext(), "选图失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 将原始 URL 编码为 ASCII-safe 的 URL（会把中文和特殊字符编码）
     */
    // helper: 将任意原始 URL 转为 ASCII-safe 的 URL 字符串
    private String encodeUrl(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            // 1. 先解码，把 %20 还原成空格，把 %E4 还原成中文等
            String decoded = java.net.URLDecoder.decode(rawUrl, "UTF-8");

            // 2. 再利用 URI 进行标准编码（自动处理空格、中文等）
            java.net.URL u = new java.net.URL(decoded);
            java.net.URI uri = new java.net.URI(
                    u.getProtocol(),
                    u.getUserInfo(),
                    u.getHost(),
                    u.getPort(),
                    u.getPath(),
                    u.getQuery(),
                    null);
            return uri.toASCIIString();
        } catch (Exception e) {
            // 如果解析失败，回退到简单的空格替换
            Log.w(TAG, "encodeUrl failed, fallback to replace", e);
            try {
                return rawUrl.replace(" ", "%20");
            } catch (Exception ignored) {}
        }
        return rawUrl;
    }

    /**
     * 根据 imagePath 准备 File：
     * 支持 http(s)://, file://, content://, android.resource://, 以及资源名（drawable/demo1）等
     */
    private File prepareFileFromPath(Context ctx, String path) {
        if (path == null) return null;
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return downloadToCache(ctx, path);
            } else if (path.startsWith("file://")) {
                Uri u = Uri.parse(path);
                return new File(u.getPath());
            } else if (path.startsWith("content://")) {
                Uri u = Uri.parse(path);
                return copyUriToCache(ctx, u, "outfit_cache.png");
            } else if (path.startsWith("android.resource://")) {
                return resourceToCache(ctx, path, "outfit_cache.png");
            } else if (path.startsWith("/")) {
                return new File(path);
            } else if (path.startsWith("drawable/") || path.startsWith("res/")) {
                return resourceToCache(ctx, path, "outfit_cache.png");
            } else if (path.startsWith("content:")) {
                Uri u = Uri.parse(path);
                return copyUriToCache(ctx, u, "outfit_cache.png");
            } else {
                if (path.startsWith("//") || path.contains("://")) {
                    return downloadToCache(ctx, path);
                }
                return resourceToCache(ctx, path, "outfit_cache.png");
            }
        } catch (Exception e) {
            Log.e(TAG, "prepareFileFromPath error for path=" + path, e);
            return null;
        }
    }

    // 更稳健的下载实现：对 URL 做编码并打印 DNS；使用 timeouts
    private File downloadToCache(Context ctx, String rawUrl) {
        String url = encodeUrl(rawUrl);
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        okhttp3.Request req;
        try {
            req = new okhttp3.Request.Builder().url(url).build();
        } catch (IllegalArgumentException iae) {
            android.util.Log.w("TryOnFragment", "Bad URL after encoding: " + url, iae);
            return null;
        }

        try (okhttp3.Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                android.util.Log.w("TryOnFragment", "downloadToCache response not successful: " + resp.code() + " for url: " + url);
                return null;
            }
            java.io.InputStream in = resp.body().byteStream();
            File f = new File(ctx.getCacheDir(), "outfit_download.png");
            try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return f;
        } catch (java.net.UnknownHostException uhe) {
            android.util.Log.w("TryOnFragment", "DNS lookup failed for url: " + url + " : " + uhe.getMessage());
            return null;
        } catch (Exception e) {
            android.util.Log.e("TryOnFragment", "downloadToCache failed for url: " + url, e);
            return null;
        }
    }


    // 把 content:// URI 拷贝到缓存
    private File copyUriToCache(Context ctx, Uri uri, String filename) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            File f = new File(ctx.getCacheDir(), filename);
            try (OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return f;
        } catch (Exception e) {
            Log.e(TAG, "copyUriToCache failed", e);
            return null;
        }
    }

    // 从 android.resource:// 或 资源名写到缓存
    private File resourceToCache(Context ctx, String path, String filename) {
        try {
            Resources res = ctx.getResources();
            String pkg = ctx.getPackageName();

            Uri u = Uri.parse(path);
            List<String> segs = u.getPathSegments();
            String resType = null;
            String resName = null;
            if (segs.size() >= 2) {
                resType = segs.get(segs.size() - 2);
                resName = segs.get(segs.size() - 1);
            } else if (segs.size() == 1) {
                resName = segs.get(0);
                resType = "drawable";
            }

            if (resName == null) {
                String last = u.getLastPathSegment();
                if (last != null) resName = last;
            }

            if (resType == null) resType = "drawable";

            int rid = res.getIdentifier(resName, resType, pkg);
            if (rid == 0) {
                if (path.contains("/")) {
                    String[] parts = path.split("/");
                    if (parts.length >= 2) {
                        resType = parts[0];
                        resName = parts[1];
                        rid = res.getIdentifier(resName, resType, pkg);
                    }
                }
            }

            if (rid == 0) return null;

            BitmapDrawable d = (BitmapDrawable) res.getDrawable(rid);
            Bitmap bmp = d.getBitmap();
            File f = new File(ctx.getCacheDir(), filename);
            try (FileOutputStream out = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
            return f;
        } catch (Exception e) {
            Log.e(TAG, "resourceToCache failed for path=" + path, e);
            return null;
        }
    }

    // ----------------------------
    // FavoritesAdapter（横向）内部实现
    // ----------------------------
    public static class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.VH> {

        public interface OnSelectListener {
            void onSelect(Outfit outfit);
        }

        private final Context ctx;
        private final List<Outfit> items = new ArrayList<>();
        private final OnSelectListener listener;
        private int selectedPos = -1;

        public FavoritesAdapter(Context ctx, OnSelectListener listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        public void submitList(List<Outfit> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_favorite_small, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Outfit o = items.get(position);
            holder.title.setText(o.title == null ? "" : o.title);
            Glide.with(ctx).load(o.imagePath).into(holder.image);

            holder.itemView.setOnClickListener(v -> {
                int old = selectedPos;
                selectedPos = position;
                notifyItemChanged(old);
                notifyItemChanged(selectedPos);
                if (listener != null) listener.onSelect(o);
            });

            holder.itemView.setSelected(position == selectedPos);
            holder.overlay.setVisibility(position == selectedPos ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView title;
            View overlay;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.iv_small);
                title = v.findViewById(R.id.tv_small_title);
                overlay = v.findViewById(R.id.v_selected_overlay);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ex.shutdownNow();
    }
}
