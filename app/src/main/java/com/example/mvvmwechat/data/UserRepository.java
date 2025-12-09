package com.example.mvvmwechat.data;


import android.content.Context;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class UserRepository {
    private final UserDao userDao;
    private final ExecutorService executorService;


    public UserRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        userDao = db.userDao();
        executorService = Executors.newSingleThreadExecutor();
    }


    public interface LoginCallback {
        void onResult(User user);
    }


    public void insertUser(final User user) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                userDao.insertUser(user);
            }
        });
    }


    public void login(final String username, final String password, final LoginCallback cb) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                User u = userDao.login(username, password);
                if (cb != null) cb.onResult(u);
            }
        });
    }
}