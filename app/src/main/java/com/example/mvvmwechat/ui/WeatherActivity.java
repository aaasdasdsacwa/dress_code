package com.example.mvvmwechat.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mvvmwechat.network.BackendApi;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mvvmwechat.R;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherActivity extends AppCompatActivity {

    private TextView tvCity;
    private TextView tvWeatherInfo;
    private TextView tvWeatherDesc;
    private Button btnSwitchCity;
    private Button btnRefresh;

    private LocationManager locationManager;
    private String currentCity = "Beijing"; // 默认城市

    private final ExecutorService ex = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        tvCity = findViewById(R.id.tv_city);
        tvWeatherInfo = findViewById(R.id.tv_weather_info);
        tvWeatherDesc = findViewById(R.id.tv_weather_desc);
        btnSwitchCity = findViewById(R.id.btn_switch_city);
        btnRefresh = findViewById(R.id.btn_refresh_weather);

        // 初始化定位
        checkPermissionAndLocate();

        // 按钮事件
        btnSwitchCity.setOnClickListener(v -> showSwitchCityDialog());
        btnRefresh.setOnClickListener(v -> fetchWeatherData(currentCity));
    }

    /**
     * 1. 权限检查与定位
     */
    private void checkPermissionAndLocate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else {
            getLocation();
        }
    }
    // --- 新增方法：根据经纬度获取天气 (最稳妥，不会404) ---
    private void fetchWeatherByCoords(double lat, double lon) {
        tvWeatherDesc.setText("正在更新...");
        ex.execute(() -> {
            try {
                // ⚠️ 请确认你的 API Key 是有效的
                String apiKey = "d75caa95c39a271f92ee599a050040f6";

                // 使用 lat 和 lon 参数，而不是 q (城市名)
                String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&lang=zh_cn&units=metric&appid=" + apiKey;

                String jsonResult = BackendApi.syncGet(url);

                // 解析数据
                JSONObject jsonObject = new JSONObject(jsonResult);
                String description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");
                double tempDouble = jsonObject.getJSONObject("main").getDouble("temp");
                String tempStr = (int)Math.round(tempDouble) + "°C";
                String cityName = jsonObject.getString("name"); // API 会返回它识别到的城市名(通常是拼音)

                runOnUiThread(() -> {
                    tvCity.setText(cityName); // 更新为服务器返回的标准地名
                    tvWeatherInfo.setText(tempStr);
                    tvWeatherDesc.setText(description);
                    Toast.makeText(WeatherActivity.this, "更新成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvWeatherDesc.setText("获取失败");
                    // 如果是 401 说明 Key 没激活；如果是 404 说明位置太偏
                    Toast.makeText(WeatherActivity.this, "错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void getLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            // 尝试获取位置
            Location lastKnownLocation = null;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnownLocation == null) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }

            if (lastKnownLocation != null) {
                // 情况A: 获取到了位置 -> 用经纬度查
                updateCityFromLocation(lastKnownLocation);
            } else {
                // 情况B: 模拟器没位置数据，或者真机没开GPS -> 【自动加载北京】
                runOnUiThread(() -> {
                    tvCity.setText("北京 (默认)");
                    Toast.makeText(this, "未获取到位置，显示默认城市", Toast.LENGTH_SHORT).show();
                    // 使用拼音 Beijing 防止乱码
                    fetchWeatherData("Beijing");
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 根据经纬度获取城市名 (反地理编码)
    // 修改后的 updateCityFromLocation
    private void updateCityFromLocation(Location location) {
        ex.execute(() -> {
            Geocoder geocoder = new Geocoder(WeatherActivity.this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String city = addresses.get(0).getLocality();
                    if (city == null) city = addresses.get(0).getSubAdminArea();
                    String finalCity = (city != null) ? city : "未知城市";

                    // 【关键修改】拿到位置后，直接用经纬度查天气，不要用中文城市名查
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    runOnUiThread(() -> {
                        tvCity.setText(finalCity); // 界面显示中文名(给用户看)
                        fetchWeatherByCoords(lat, lon); // 后台用经纬度查天气(给API看)
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 2. 切换城市功能 (弹出输入框)
     */
    private void showSwitchCityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("切换城市");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入城市名称，例如：上海");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newCity = input.getText().toString().trim();
            if (!newCity.isEmpty()) {
                currentCity = newCity;
                tvCity.setText(currentCity);
                fetchWeatherData(currentCity);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * 3. 获取天气数据
     * 注意：真实的 App 需要申请 API Key (如和风天气、OpenWeather)。
     * 这里为了演示代码逻辑，使用了模拟数据。如果需要真实数据，请看注释部分。
     */
    private void fetchWeatherData(String cityName) {
        tvWeatherDesc.setText("正在更新天气...");

        ex.execute(() -> {
            try {
                // 1. 准备真实的 URL 和 Key
                // ⚠️请将 YOUR_API_KEY 替换为你自己申请的 Key
                String apiKey = "d75caa95c39a271f92ee599a050040f6";

                // 组装 URL：地址 + 城市 + 语言(中文) + 单位(公制/摄氏度) + Key
                String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&lang=zh_cn&units=metric&appid=" + apiKey;

                // 2. 发起网络请求 (使用你现有的 BackendApi 工具)
                String jsonResult = BackendApi.syncGet(url);

                // 3. 解析返回的 JSON 数据
                // OpenWeatherMap 返回的数据结构大概是：
                // { "weather": [{"description": "晴"}], "main": {"temp": 25.5}, ... }
                JSONObject jsonObject = new JSONObject(jsonResult);

                // 获取天气描述 (例如：多云)
                String description = jsonObject.getJSONArray("weather")
                        .getJSONObject(0)
                        .getString("description");

                // 获取温度 (例如：25)
                double tempDouble = jsonObject.getJSONObject("main").getDouble("temp");
                String tempStr = (int)Math.round(tempDouble) + "°C";

                // 4. 更新 UI
                runOnUiThread(() -> {
                    tvWeatherInfo.setText(tempStr);
                    tvWeatherDesc.setText(description);
                    // 城市名有时候 API 返回的更标准，也可以更新一下
                    // tvCity.setText(jsonObject.getString("name"));
                    Toast.makeText(WeatherActivity.this, "更新成功", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    tvWeatherDesc.setText("获取失败");
                    Toast.makeText(WeatherActivity.this, "错误：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation();
            } else {
                Toast.makeText(this, "需要定位权限才能显示当前城市", Toast.LENGTH_SHORT).show();
            }
        }
    }
}