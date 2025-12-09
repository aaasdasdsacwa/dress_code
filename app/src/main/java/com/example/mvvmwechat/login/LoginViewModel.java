package com.example.mvvmwechat.login;


import android.app.Application;


import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import com.example.mvvmwechat.data.User;
import com.example.mvvmwechat.data.UserRepository;


public class LoginViewModel extends AndroidViewModel {
    private final UserRepository repository;
    private final MutableLiveData<Boolean> loginResult = new MutableLiveData<>();


    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new UserRepository(application.getApplicationContext());
    }


    public LiveData<Boolean> getLoginResult() {
        return loginResult;
    }


    public void register(final String username, final String password) {
        repository.insertUser(new User(username, password));
    }


    public void login(final String username, final String password) {
        repository.login(username, password, new UserRepository.LoginCallback() {
            @Override
            public void onResult(User user) {
                loginResult.postValue(user != null);
            }
        });
    }
}