package com.bookwhere.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// B2 책 검색 (담당: 박종혁)
public class SearchFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        // 임시 버튼: 책 상세 화면 이동 테스트. 실제로는 검색 결과를 누르면 이렇게 넘기면 됨.
        view.findViewById(R.id.btn_test_detail).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DetailActivity.class);
            intent.putExtra(DetailActivity.EXTRA_BOOK_ID, "총_균_쇠"); // Firestore books 문서 ID
            startActivity(intent);
        });
        return view;
    }
}
