package com.bookwhere.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// B4 찜한 책 (담당: 고은성)
public class WishlistFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);

        // "구독 추천 보기" → B6
        view.findViewById(R.id.btn_recommend).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecommendActivity.class)));

        // TODO: users/{uid}/wishlist 불러와서 목록 표시
        return view;
    }
}
