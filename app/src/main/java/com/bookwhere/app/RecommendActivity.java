package com.bookwhere.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// B6 구독 추천 (담당: 고은성) — 하단 탭 없음, 찜한 책 화면의 버튼으로 들어옴
public class RecommendActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        // TODO: 찜한 책 + books의 platforms + plans 가격으로 추천 계산
    }
}
