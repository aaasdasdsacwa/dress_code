package com.example.mvvmwechat;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.mvvmwechat.ui.CollectionFragment;
import com.example.mvvmwechat.ui.OutfitsFragment;
import com.example.mvvmwechat.ui.TryOnFragment;
import com.example.mvvmwechat.ui.BackendFragment;
import com.example.mvvmwechat.ui.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {

    // 可根据需要在这里保存当前选中的 menu id
    private static final int DEFAULT_MENU_ID = R.id.menu_outfits;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_navigation);

        // 如果 activity 因配置改变或进程重建而恢复，可以让 BottomNavigation 恢复选中项并避免重复创建 fragment
        if (savedInstanceState == null) {
            // 首次启动显示 Outfits（穿搭展示）模块
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, new OutfitsFragment())
                    .commit();
            // 设置 bottomNavigation 默认选中项（可选）
            nav.setSelectedItemId(DEFAULT_MENU_ID);
        }

        // 使用 if/else 代替 switch，避免资源 id 非编译期常量的问题
        nav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selected = null;
                int id = item.getItemId();

                if (id == R.id.menu_outfits) {
                    selected = new OutfitsFragment();
                } else if (id == R.id.menu_tryon) {
                    selected = new TryOnFragment();
                } else if (id == R.id.menu_backend) {
                    selected = new BackendFragment();
                } else if (id == R.id.menu_collection) {
                    selected = new CollectionFragment();
                } else if (id == R.id.menu_settings) {
                    selected = new SettingsFragment();
                }

                if (selected != null) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.container, selected)
                            .commit();
                }

                return true;
            }
        });
    }
}
