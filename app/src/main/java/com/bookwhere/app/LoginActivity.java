package com.bookwhere.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// B1 로그인 (담당: 구진희)
// 지금은 버튼 누르면 바로 메인으로 감. Firebase 로그인은 여기에 붙이면 됨.
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        findViewById(R.id.btn_login).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish(); // 뒤로가기로 로그인 화면에 다시 안 오게
        });
    }
}
