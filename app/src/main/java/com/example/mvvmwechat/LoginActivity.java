package com.example.mvvmwechat;


import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;


import com.example.mvvmwechat.login.LoginViewModel;


public class LoginActivity extends AppCompatActivity {


    private EditText etUser, etPass;
    private Button btnLogin, btnRegister;
    private LoginViewModel viewModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);


        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        viewModel.getLoginResult().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean success) {
                if (success != null && success) {
                    Toast.makeText(LoginActivity.this, "Login success", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                }
            }
        });


        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = etUser.getText().toString().trim();
                String p = etPass.getText().toString().trim();
                if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
                    Toast.makeText(LoginActivity.this, "Enter username and password", Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.register(u, p);
                Toast.makeText(LoginActivity.this, "Registered (local)", Toast.LENGTH_SHORT).show();
            }
        });


        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = etUser.getText().toString().trim();
                String p = etPass.getText().toString().trim();
                if (TextUtils.isEmpty(u) || TextUtils.isEmpty(p)) {
                    Toast.makeText(LoginActivity.this, "Enter username and password", Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.login(u, p);
            }
        });
    }
}