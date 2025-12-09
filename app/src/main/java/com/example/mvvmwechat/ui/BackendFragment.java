package com.example.mvvmwechat.ui;

import android.content.Intent; // 导入 Intent
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mvvmwechat.R;
import com.example.mvvmwechat.network.BackendApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackendFragment extends Fragment {
    private TextView tvResult;
    private Button btnRefresh;
    private Button btnWeather; // 新增按钮变量
    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_backend, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        tvResult = view.findViewById(R.id.tv_result);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnWeather = view.findViewById(R.id.btn_weather); // 绑定视图

        btnRefresh.setOnClickListener(v -> fetchFromBackend());

        // 新增：点击跳转到 WeatherActivity
        btnWeather.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), WeatherActivity.class);
            startActivity(intent);
        });
    }

    private void fetchFromBackend() {
        tvResult.setText("加载中...");
        ex.execute(() -> {
            try {
                // 这里假设你的 BackendApi 类工作正常
                String json = BackendApi.syncGet("/outfits");
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> tvResult.setText(json));
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvResult.setText("获取失败：" + e.getMessage());
                        Toast.makeText(getActivity(), "请求失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}