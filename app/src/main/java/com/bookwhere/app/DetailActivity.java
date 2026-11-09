package com.bookwhere.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

// B3 책 상세 (담당: 박종혁) — 하단 탭 없음
public class DetailActivity extends AppCompatActivity {

    // 검색 화면에서 넘겨주는 책 문서 ID (예: "총_균_쇠")
    public static final String EXTRA_BOOK_ID = "bookId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        String bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        // TODO: Firestore books/{bookId} 불러와서 화면에 표시, 찜 버튼
    }
}
