package com.bookwhere.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// B2 책 검색
// books 전체를 한 번 불러온 뒤, 검색창 글자가 제목이나 저자에 들어간 책만 보여줌
public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";

    private final List<Book> allBooks = new ArrayList<>(); // Firestore에서 불러온 책 전체
    private boolean loaded = false;                         // 불러오기가 끝났는지

    private SearchBookAdapter adapter;
    private EditText etSearch;
    private TextView tvCount;
    private TextView tvEmpty;
    private ProgressBar progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        etSearch = view.findViewById(R.id.et_search);
        tvCount = view.findViewById(R.id.tv_search_count);
        tvEmpty = view.findViewById(R.id.tv_search_empty);
        progress = view.findViewById(R.id.progress_search);

        RecyclerView rv = view.findViewById(R.id.rv_search);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2)); // B안: 2열 표지 그리드
        adapter = new SearchBookAdapter(this::openDetail);
        rv.setAdapter(adapter);

        // 글자를 입력할 때마다 바로 걸러서 보여줌
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        // 키보드의 검색 버튼을 누르면 키보드만 닫기 (목록은 이미 걸러져 있음)
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        loadBooks();
        return view;
    }

    /** Firestore books 컬렉션 전체 불러오기 */
    private void loadBooks() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        FirebaseFirestore.getInstance()
                .collection("books")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (getView() == null) return; // 그 사이 다른 탭으로 이동함

                    allBooks.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        allBooks.add(doc.toObject(Book.class));
                    }
                    loaded = true;
                    progress.setVisibility(View.GONE);
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "books 불러오기 실패", e);
                    if (getView() == null) return;

                    progress.setVisibility(View.GONE);
                    tvCount.setText("");
                    tvEmpty.setText("책 목록을 불러오지 못했어요.\n인터넷 연결을 확인해 주세요.");
                    tvEmpty.setVisibility(View.VISIBLE);
                });
    }

    /** 검색창 글자로 allBooks를 걸러서 목록에 표시 */
    private void applyFilter() {
        if (!loaded) return;

        String typed = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        String query = normalize(typed);

        List<Book> result = new ArrayList<>();
        for (Book book : allBooks) {
            if (query.isEmpty()
                    || normalize(book.getTitle()).contains(query)
                    || normalize(book.getAuthor()).contains(query)) {
                result.add(book);
            }
        }
        adapter.setBooks(result);

        if (query.isEmpty()) {
            tvCount.setText("전체 도서 " + allBooks.size() + "권");
        } else {
            tvCount.setText("검색 결과 " + result.size() + "권");
        }

        if (result.isEmpty()) {
            tvEmpty.setText(query.isEmpty()
                    ? "등록된 책이 없어요."
                    : "검색 결과가 없어요. 다른 제목이나 작가 이름으로 찾아보세요.");
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    /** 비교용: 대소문자와 띄어쓰기 무시 ("총균쇠"로 "총 균 쇠"도 찾음) */
    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 목록 항목을 누르면 B3 책 상세로 이동 */
    private void openDetail(Book book) {
        Intent intent = new Intent(requireContext(), DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_BOOK_ID, book.getId());
        startActivity(intent);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm =
                (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        v.clearFocus();
    }
}
