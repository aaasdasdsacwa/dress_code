package com.example.mvvmwechat.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.mvvmwechat.R;
import com.example.mvvmwechat.data.AppDatabase; // 引入数据库以便读取设置
import com.example.mvvmwechat.data.Outfit;
import com.example.mvvmwechat.viewmodel.OutfitViewModel;

import java.util.ArrayList;
import java.util.Collections; // 用于排序
import java.util.Comparator;  // 用于自定义排序规则
import java.util.List;
import java.util.concurrent.ExecutorService; // 用于后台读取设置
import java.util.concurrent.Executors;

/**
 * OutfitsFragment (MVVM 版 + 排序功能):
 * - 结合了设置模块的性别设置
 * - 新增：结合设置模块的默认展示顺序（风格/季节/天气）
 */
public class OutfitsFragment extends Fragment {

    private OutfitViewModel viewModel;
    private RecyclerView recyclerView;
    private OutfitAdapter adapter;
    private List<Outfit> fullList = new ArrayList<>(); // 内存中保存一份完整数据用于筛选

    // 筛选条件变量
    private String selGender = "all"; // 这个值会根据数据库设置自动更新
    private String selStyle = "";
    private String selSeason = "";
    private String selWeather = "";
    private String selScene = "";
    private String selKeyword = "";

    // 【新增】当前排序模式，默认为 style
    private String currentSortMode = "style";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_outfits, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(OutfitViewModel.class);

        recyclerView = view.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));

        adapter = new OutfitAdapter(requireContext(), new OutfitAdapter.OnItemClickListener() {
            @Override
            public void onFavoriteClick(Outfit outfit) {
                // 点击爱心：执行收藏
                viewModel.addFavorite(outfit.id);
                Toast.makeText(requireContext(), "已收藏：" + outfit.title, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemClick(Outfit outfit) {
                // 点击图片/卡片
                Toast.makeText(requireContext(), "查看详情：" + outfit.title, Toast.LENGTH_SHORT).show();
            }
        });

        recyclerView.setAdapter(adapter);

        // 观察穿搭数据 (从 Python/本地数据库获取)
        viewModel.loadAll().observe(getViewLifecycleOwner(), outfits -> {
            fullList.clear();
            if (outfits != null) fullList.addAll(outfits);

            // 数据加载完成后，应用当前的筛选条件和排序
            applyFiltersAndShow();
        });

        // 设置搜索框监听
        EditText etSearch = view.findViewById(R.id.etSearch);
        ImageButton btnFilter = view.findViewById(R.id.btnFilter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                selKeyword = s.toString().trim();
                applyFiltersAndShow(); // 实时搜索
            }
        });

        btnFilter.setOnClickListener(v -> showFilterDialog());
    }

    /**
     * ⚠️ 生命周期 onResume
     * 每次回到页面，重新读取数据库里的设置 (性别 + 排序模式)
     */
    @Override
    public void onResume() {
        super.onResume();
        loadSettingsAndRefresh();
    }

    /**
     * 【新增】读取设置并刷新列表
     * 这里使用 ExecutorService 直接查库，与 SettingsFragment 逻辑保持一致
     */
    private void loadSettingsAndRefresh() {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(() -> {
            if (getContext() == null) return;
            AppDatabase db = AppDatabase.getInstance(requireContext());

            // 1. 读取性别设置
            String genderSetting = db.settingDao().get("gender");
            // 2. 读取排序模式设置
            String sortSetting = db.settingDao().get("sort_mode");

            // 切换回主线程更新 UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 更新性别变量
                    if (genderSetting != null) {
                        this.selGender = genderSetting;
                    }
                    // 更新排序变量
                    if (sortSetting != null) {
                        this.currentSortMode = sortSetting;
                    }
                    // 应用新设置
                    applyFiltersAndShow();
                });
            }
        });
    }

    /**
     * 本地筛选 + 排序逻辑
     */
    private void applyFiltersAndShow() {
        if (fullList == null) {
            adapter.submitList(new ArrayList<>());
            return;
        }
        List<Outfit> out = new ArrayList<>();

        String g = (selGender == null) ? "all" : selGender.toLowerCase();
        String s = (selStyle == null) ? "" : selStyle.toLowerCase();
        String se = (selSeason == null) ? "" : selSeason.toLowerCase();
        String w = (selWeather == null) ? "" : selWeather.toLowerCase();
        String sc = (selScene == null) ? "" : selScene.toLowerCase();
        String kw = (selKeyword == null) ? "" : selKeyword.toLowerCase();

        // --- 1. 执行筛选 ---
        for (Outfit o : fullList) {
            boolean ok = true;

            // 性别筛选
            if (!g.isEmpty() && !g.equals("all")) {
                String og = (o.gender == null) ? "" : o.gender.toLowerCase();
                if (!og.equals("unisex") && !og.equals(g)) ok = false;
            }
            // 风格筛选
            if (ok && !s.isEmpty()) {
                String os = (o.style == null) ? "" : o.style.toLowerCase();
                if (!os.contains(s)) ok = false;
            }
            // 季节筛选
            if (ok && !se.isEmpty()) {
                String ose = (o.season == null) ? "" : o.season.toLowerCase();
                if (!ose.contains(se)) ok = false;
            }
            // 天气筛选
            if (ok && !w.isEmpty()) {
                String ow = (o.weather == null) ? "" : o.weather.toLowerCase();
                if (!ow.contains(w)) ok = false;
            }
            // 场景筛选
            if (ok && !sc.isEmpty()) {
                String osc = (o.scene == null) ? "" : o.scene.toLowerCase();
                if (!osc.contains(sc)) ok = false;
            }
            // 关键字搜索
            if (ok && !kw.isEmpty()) {
                String title = (o.title == null) ? "" : o.title.toLowerCase();
                String styleField = (o.style == null) ? "" : o.style.toLowerCase();
                if (!title.contains(kw) && !styleField.contains(kw)) ok = false;
            }

            if (ok) out.add(o);
        }

        // --- 2. 【新增】执行排序 ---
        sortList(out);

        // --- 3. 提交数据 ---
        adapter.submitList(out);
    }

    /**
     * 【新增】根据 currentSortMode 对列表进行排序
     */
    private void sortList(List<Outfit> list) {
        if (list == null || list.isEmpty()) return;

        switch (currentSortMode) {
            case "season":
                // 按季节顺序排序：春 -> 夏 -> 秋 -> 冬
                Collections.sort(list, new Comparator<Outfit>() {
                    @Override
                    public int compare(Outfit o1, Outfit o2) {
                        return getSeasonScore(o1.season) - getSeasonScore(o2.season);
                    }
                });
                break;

            case "weather":
                // 按天气顺序排序：晴 -> 阴 -> 雨 -> 雪
                Collections.sort(list, new Comparator<Outfit>() {
                    @Override
                    public int compare(Outfit o1, Outfit o2) {
                        return getWeatherScore(o1.weather) - getWeatherScore(o2.weather);
                    }
                });
                break;

            case "style":
            default:
                // 按风格名称（或标题）排序
                Collections.sort(list, new Comparator<Outfit>() {
                    @Override
                    public int compare(Outfit o1, Outfit o2) {
                        String s1 = o1.style == null ? "" : o1.style;
                        String s2 = o2.style == null ? "" : o2.style;
                        return s1.compareTo(s2);
                    }
                });
                break;
        }
    }

    // 【新增】辅助方法：给季节赋予数字权重以便排序
    private int getSeasonScore(String season) {
        if (season == null) return 99;
        if (season.contains("春")) return 1;
        if (season.contains("夏")) return 2;
        if (season.contains("秋")) return 3;
        if (season.contains("冬")) return 4;
        return 99; // 未知季节排最后
    }

    // 【新增】辅助方法：给天气赋予数字权重以便排序
    private int getWeatherScore(String weather) {
        if (weather == null) return 99;
        if (weather.contains("晴")) return 1;
        if (weather.contains("阴")) return 2;
        if (weather.contains("雨")) return 3;
        if (weather.contains("雪")) return 4;
        return 99;
    }

    /**
     * 显示筛选对话框
     */
    private void showFilterDialog() {
        LayoutInflater inf = LayoutInflater.from(requireContext());
        View dialogView = inf.inflate(R.layout.dialog_filter, null);

        Spinner spGender = dialogView.findViewById(R.id.sp_gender);
        Spinner spStyle = dialogView.findViewById(R.id.sp_style);
        Spinner spSeason = dialogView.findViewById(R.id.sp_season);
        Spinner spWeather = dialogView.findViewById(R.id.sp_weather);
        Spinner spScene = dialogView.findViewById(R.id.sp_scene);

        setupSpinner(spGender, new String[]{"all", "male", "female", "unisex"}, selGender);
        setupSpinner(spStyle, new String[]{"", "甜美", "休闲", "职业", "运动"}, selStyle);
        setupSpinner(spSeason, new String[]{"", "春", "夏", "秋", "冬"}, selSeason);
        setupSpinner(spWeather, new String[]{"", "晴", "雨", "雪", "阴"}, selWeather);
        setupSpinner(spScene, new String[]{"", "约会", "上学", "工作", "运动"}, selScene);

        new AlertDialog.Builder(requireContext())
                .setTitle("筛选条件")
                .setView(dialogView)
                .setPositiveButton("应用", (dialog, which) -> {
                    selGender = getSpinnerValue(spGender, "all");
                    selStyle = getSpinnerValue(spStyle, "");
                    selSeason = getSpinnerValue(spSeason, "");
                    selWeather = getSpinnerValue(spWeather, "");
                    selScene = getSpinnerValue(spScene, "");
                    applyFiltersAndShow();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupSpinner(Spinner spinner, String[] items, String currentValue) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        setSpinnerSelectionByValue(spinner, currentValue);
    }

    private String getSpinnerValue(Spinner spinner, String defaultValue) {
        if (spinner.getSelectedItem() == null) return defaultValue;
        return spinner.getSelectedItem().toString();
    }

    private void setSpinnerSelectionByValue(Spinner sp, String value) {
        if (value == null) return;
        ArrayAdapter adapter = (ArrayAdapter) sp.getAdapter();
        if (adapter == null) return;
        for (int i = 0; i < adapter.getCount(); i++) {
            Object it = adapter.getItem(i);
            if (it != null && value.equals(it.toString())) {
                sp.setSelection(i);
                return;
            }
        }
    }

    // --------------------------------
    // Adapter
    // --------------------------------
    public static class OutfitAdapter extends RecyclerView.Adapter<OutfitAdapter.VH> {

        public interface OnItemClickListener {
            void onFavoriteClick(Outfit outfit);
            void onItemClick(Outfit outfit);
        }

        private final Context ctx;
        private final List<Outfit> list = new ArrayList<>();
        private final OnItemClickListener listener;

        public OutfitAdapter(Context ctx, OnItemClickListener listener) {
            this.ctx = ctx;
            this.listener = listener;
        }

        public void submitList(List<Outfit> newList) {
            list.clear();
            if (newList != null) list.addAll(newList);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_outfit, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Outfit o = list.get(position);
            holder.title.setText(o.title == null ? "" : o.title);

            Glide.with(ctx)
                    .load(o.imagePath)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.img);

            holder.btnFavorite.setOnClickListener(v -> {
                if (listener != null) listener.onFavoriteClick(o);
            });

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(o);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView img;
            android.widget.TextView title;
            android.widget.ImageView btnFavorite;

            VH(@NonNull View v) {
                super(v);
                img = v.findViewById(R.id.outfit_img);
                title = v.findViewById(R.id.outfit_title);
                btnFavorite = v.findViewById(R.id.iv_favorite);
            }
        }
    }
}