package com.example.mvvmwechat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast; // 记得导入 Toast

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.mvvmwechat.R;
import com.example.mvvmwechat.data.AppDatabase;
import com.example.mvvmwechat.data.Favorite;
import com.example.mvvmwechat.data.Outfit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CollectionFragment: 读取 favorites 并展示对应的 Outfit 项
 */
public class CollectionFragment extends Fragment {

    private RecyclerView rv;
    private OutfitsFragment.OutfitAdapter adapter;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rv = view.findViewById(R.id.rv_collection);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));

        // ⚠️ 修复报错：使用匿名内部类，实现两个方法
        adapter = new OutfitsFragment.OutfitAdapter(requireContext(), new OutfitsFragment.OutfitAdapter.OnItemClickListener() {
            @Override
            public void onFavoriteClick(Outfit outfit) {
                // 这里是收藏页面，通常再次点击爱心表示“取消收藏”
                // 你可以稍后在这里实现删除逻辑，暂时先弹个窗
                Toast.makeText(requireContext(), "已在收藏列表中", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemClick(Outfit outfit) {
                // 点击图片查看详情
                Toast.makeText(requireContext(), "查看穿搭：" + outfit.title, Toast.LENGTH_SHORT).show();
            }
        });

        rv.setAdapter(adapter);

        loadFavorites();
    }

    private void loadFavorites() {
        ex.execute(() -> {
            // 你的加载逻辑保持不变
            List<Favorite> favs = AppDatabase.getInstance(requireContext()).favoriteDao().getAll();
            List<Outfit> all = AppDatabase.getInstance(requireContext()).outfitDao().getAll();
            HashMap<Long, Outfit> map = new HashMap<>();
            if (all != null) {
                for (Outfit o : all) map.put(o.id, o);
            }
            List<Outfit> results = new ArrayList<>();
            if (favs != null) {
                for (Favorite f : favs) {
                    Outfit o = map.get(f.outfitId);
                    if (o != null) results.add(o);
                }
            }
            final List<Outfit> finalResults = results;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.submitList(finalResults));
            }
        });
    }
}