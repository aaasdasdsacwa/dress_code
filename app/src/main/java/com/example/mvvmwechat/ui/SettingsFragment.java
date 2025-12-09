package com.example.mvvmwechat.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mvvmwechat.R;
import com.example.mvvmwechat.data.AppDatabase;
import com.example.mvvmwechat.data.Setting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private RadioGroup rgGender;
    private RadioGroup rgSortMode; // 新增
    private Spinner spDefaultStyle;
    private Button btnSave;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rgGender = view.findViewById(R.id.rg_gender);
        rgSortMode = view.findViewById(R.id.rg_sort_mode); // 绑定ID
        spDefaultStyle = view.findViewById(R.id.sp_default_style);
        btnSave = view.findViewById(R.id.btn_save_settings);

        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"甜美", "休闲", "职业", "运动"});
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDefaultStyle.setAdapter(styleAdapter);

        loadSettings();

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        ex.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            String gender = db.settingDao().get("gender");
            String defaultStyle = db.settingDao().get("default_style");
            // 读取排序模式，默认为 "style"
            String sortMode = db.settingDao().get("sort_mode");
            if (sortMode == null) sortMode = "style";

            final String finalSortMode = sortMode;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 1. 恢复性别设置
                    if ("male".equals(gender)) rgGender.check(R.id.rb_male);
                    else if ("female".equals(gender)) rgGender.check(R.id.rb_female);
                    else rgGender.check(R.id.rb_all);

                    // 2. 恢复排序设置 (新增逻辑)
                    if ("season".equals(finalSortMode)) rgSortMode.check(R.id.rb_sort_season);
                    else if ("weather".equals(finalSortMode)) rgSortMode.check(R.id.rb_sort_weather);
                    else rgSortMode.check(R.id.rb_sort_style);

                    // 3. 恢复Spinner
                    if (defaultStyle != null) {
                        for (int i = 0; i < spDefaultStyle.getCount(); i++) {
                            Object it = spDefaultStyle.getItemAtPosition(i);
                            if (it != null && it.toString().equals(defaultStyle)) {
                                spDefaultStyle.setSelection(i);
                                break;
                            }
                        }
                    }
                });
            }
        });
    }

    private void saveSettings() {
        // 1. 获取性别
        String gender = "all";
        int checkedGender = rgGender.getCheckedRadioButtonId();
        if (checkedGender == R.id.rb_male) gender = "male";
        else if (checkedGender == R.id.rb_female) gender = "female";

        // 2. 获取排序模式 (新增逻辑)
        String sortMode = "style";
        int checkedSort = rgSortMode.getCheckedRadioButtonId();
        if (checkedSort == R.id.rb_sort_season) sortMode = "season";
        else if (checkedSort == R.id.rb_sort_weather) sortMode = "weather";

        // 3. 获取风格
        String defaultStyle = spDefaultStyle.getSelectedItem() == null ? "" : spDefaultStyle.getSelectedItem().toString();

        final String g = gender;
        final String sm = sortMode;
        final String ds = defaultStyle;

        ex.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            db.settingDao().put(new Setting("gender", g));
            db.settingDao().put(new Setting("sort_mode", sm)); // 保存排序模式
            db.settingDao().put(new Setting("default_style", ds));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show());
            }
        });
    }
}