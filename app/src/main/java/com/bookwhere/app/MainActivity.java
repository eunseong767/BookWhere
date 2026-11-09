package com.bookwhere.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

// 하단 탭 3개(검색/찜한 책/마이)를 관리하는 화면
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) {
                show(new SearchFragment());
            } else if (id == R.id.nav_wishlist) {
                show(new WishlistFragment());
            } else if (id == R.id.nav_mypage) {
                show(new MyPageFragment());
            }
            return true;
        });

        // 처음 열면 검색 탭
        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_search);
        }
    }

    private void show(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
