package com.bookwhere.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SearchFragment extends Fragment {
    private static final String TAG = "SearchFragment";
    private static final int RECOMMENDATION_COUNT = 6;
    private static final long SEARCH_DELAY_MS = 400;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Book> dailyBooks = new ArrayList<>();
    private final Set<String> wishedIds = new HashSet<>();
    private final Set<String> wishedCategories = new HashSet<>();
    private final List<SearchKakaoClient.Result> results = new ArrayList<>();

    private SearchBookMatcher matcher;
    private boolean catalogFailed;
    private boolean wishlistReady;
    private ListenerRegistration wishlistListener;
    private ExecutorService searchExecutor;
    private Future<?> searchRequest;
    private Runnable scheduledSearch;
    private String query = "";
    private int nextPage = 1;
    private int requestVersion;
    private boolean loading;
    private boolean hasMore;
    private String searchError;

    private RecyclerView list;
    private SearchBookAdapter adapter;
    private EditText etSearch;
    private TextView tvCount;
    private TextView tvEmpty;
    private ProgressBar progress;
    private Button btnMore;

    public SearchFragment() {
        super(R.layout.fragment_search);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        etSearch = view.findViewById(R.id.et_search);
        tvCount = view.findViewById(R.id.tv_search_count);
        tvEmpty = view.findViewById(R.id.tv_search_empty);
        progress = view.findViewById(R.id.progress_search);
        btnMore = view.findViewById(R.id.btn_search_more);

        list = view.findViewById(R.id.rv_search);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new SearchBookAdapter(this::openDetail);
        list.setAdapter(adapter);
        searchExecutor = Executors.newFixedThreadPool(2);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String typed = s.toString().trim();
                if (!query.equals(typed)) startSearch(typed, SEARCH_DELAY_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard(v);
            if (scheduledSearch != null || searchError != null) startSearch(query, 0);
            return true;
        });

        btnMore.setOnClickListener(v -> {
            boolean retryCatalog = catalogFailed;
            if (retryCatalog) loadBooks(view);
            if (!query.isEmpty() && !loading && (searchError != null || (!retryCatalog && hasMore))) {
                loadNextPage();
            }
        });

        loadBooks(view);
    }

    @Override
    public void onStart() {
        super.onStart();
        observeWishlist();
    }

    @Override
    public void onStop() {
        if (wishlistListener != null) {
            wishlistListener.remove();
            wishlistListener = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        cancelSearch();
        searchExecutor.shutdownNow();
        list.setAdapter(null);
        list = null;
        adapter = null;
        etSearch = null;
        tvCount = null;
        tvEmpty = null;
        progress = null;
        btnMore = null;
        query = "";
        searchError = null;
        loading = false;
        hasMore = false;
        nextPage = 1;
        results.clear();
        super.onDestroyView();
    }

    private void loadBooks(View view) {
        catalogFailed = false;
        render();
        FirebaseFirestore.getInstance().collection("books").get()
                .addOnSuccessListener(snapshot -> {
                    if (getView() != view) return;
                    dailyBooks.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        dailyBooks.add(doc.toObject(Book.class));
                    }
                    Calendar today = Calendar.getInstance();
                    long daySeed = today.get(Calendar.YEAR) * 1000L + today.get(Calendar.DAY_OF_YEAR);
                    Collections.shuffle(dailyBooks, new Random(daySeed));
                    matcher = new SearchBookMatcher(dailyBooks);
                    render();
                })
                .addOnFailureListener(e -> {
                    if (getView() != view) return;
                    Log.w(TAG, "books 불러오기 실패", e);
                    catalogFailed = true;
                    render();
                });
    }

    private void observeWishlist() {
        View view = requireView();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            wishedIds.clear();
            wishedCategories.clear();
            wishlistReady = true;
            render();
            return;
        }

        wishlistListener = FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid()).collection("wishlist")
                .addSnapshotListener((snapshot, error) -> {
                    if (getView() != view || wishlistListener == null) return;
                    if (error != null) Log.w(TAG, "wishlist 불러오기 실패", error);
                    if (snapshot != null) {
                        wishedIds.clear();
                        wishedCategories.clear();
                        for (QueryDocumentSnapshot doc : snapshot) {
                            wishedIds.add(doc.getId());
                            String category = doc.getString("category");
                            if (category != null && !category.isEmpty()) wishedCategories.add(category);
                        }
                    }
                    wishlistReady = true;
                    if (query.isEmpty()) render();
                });
    }

    private void startSearch(String newQuery, long delay) {
        cancelSearch();
        query = newQuery;
        results.clear();
        nextPage = 1;
        searchError = null;
        hasMore = false;
        loading = !query.isEmpty();
        list.scrollToPosition(0);
        render();
        if (!query.isEmpty()) {
            scheduledSearch = this::loadNextPage;
            main.postDelayed(scheduledSearch, delay);
        }
    }

    private void cancelSearch() {
        requestVersion++;
        if (scheduledSearch != null) {
            main.removeCallbacks(scheduledSearch);
            scheduledSearch = null;
        }
        if (searchRequest != null) {
            searchRequest.cancel(true);
            searchRequest = null;
        }
    }

    private void loadNextPage() {
        scheduledSearch = null;
        int version = ++requestVersion;
        String requestedQuery = query;
        int page = nextPage;
        Context context = requireContext().getApplicationContext();
        loading = true;
        searchError = null;
        render();

        searchRequest = searchExecutor.submit(() -> {
            try {
                SearchKakaoClient.Page response = SearchKakaoClient.search(context, requestedQuery, null, page);
                main.post(() -> {
                    if (version != requestVersion) return;
                    results.addAll(response.results);
                    hasMore = response.hasMore;
                    nextPage = page + 1;
                    loading = false;
                    render();
                });
            } catch (IOException | JSONException e) {
                main.post(() -> {
                    if (version != requestVersion) return;
                    Log.w(TAG, "카카오 책 검색 실패", e);
                    loading = false;
                    searchError = "검색 결과를 불러오지 못했어요.";
                    render();
                });
            }
        });
    }

    private List<SearchBookAdapter.Item> recommendations() {
        List<Book> ranked = new ArrayList<>(dailyBooks);
        ranked.sort(Comparator.comparingInt(this::recommendationPriority));
        List<SearchBookAdapter.Item> items = new ArrayList<>();
        for (Book book : ranked.subList(0, Math.min(RECOMMENDATION_COUNT, ranked.size()))) {
            items.add(new SearchBookAdapter.Item(book, null));
        }
        return items;
    }

    private int recommendationPriority(Book book) {
        if (wishedIds.contains(book.getId())) return 2;
        return wishedCategories.contains(book.getCategory()) ? 0 : 1;
    }

    private void render() {
        boolean isHome = query.isEmpty();
        boolean catalogLoading = matcher == null && !catalogFailed;
        boolean waiting = catalogLoading || (isHome ? !wishlistReady : loading);
        List<SearchBookAdapter.Item> items = new ArrayList<>();
        String message = null;
        if (isHome) {
            tvCount.setText("오늘의 책 추천");
            if (!waiting) items = recommendations();
            if (catalogFailed && matcher == null) {
                message = "추천 도서 목록을 불러오지 못했어요.";
            } else if (!waiting && items.isEmpty()) {
                message = "등록된 책이 없어요.";
            }
        } else {
            if (!catalogLoading) {
                items = SearchBookMatcher.uniqueResults(results, matcher);
            }
            tvCount.setText(waiting && items.isEmpty() ? "검색 중"
                    : "검색 결과 " + items.size() + "권");
            if (catalogFailed) {
                tvCount.append("\n구독 정보를 불러오지 못했어요.");
            }
            if (items.isEmpty()) {
                if (searchError != null) message = searchError;
                else if (!waiting) message = "검색 결과가 없어요. 다른 제목이나 작가 이름으로 찾아보세요.";
            }
        }

        adapter.setBooks(items);
        tvEmpty.setText(message);
        tvEmpty.setVisibility(message == null ? View.GONE : View.VISIBLE);
        progress.setVisibility(waiting && message == null ? View.VISIBLE : View.GONE);
        boolean retry = catalogFailed || searchError != null;
        boolean showMore = !isHome && hasMore && !catalogLoading;
        btnMore.setText(retry ? "다시 시도" : "더 보기");
        btnMore.setVisibility(retry || showMore ? View.VISIBLE : View.GONE);
        btnMore.setEnabled(!loading);
    }

    private void openDetail(SearchBookAdapter.Item item) {
        if (matcher == null) {
            loadBooks(requireView());
            return;
        }
        Intent intent = new Intent(requireContext(), DetailActivity.class);
        if (item.book != null) intent.putExtra(DetailActivity.EXTRA_BOOK_ID, item.book.getId());
        if (item.result != null) {
            intent.putExtra(DetailActivity.EXTRA_TITLE, item.result.title);
            intent.putExtra(DetailActivity.EXTRA_AUTHOR, item.result.author);
            intent.putExtra(DetailActivity.EXTRA_PUBLISHER, item.result.publisher);
            intent.putExtra(DetailActivity.EXTRA_THUMBNAIL, item.result.thumbnail);
            intent.putExtra(DetailActivity.EXTRA_CONTENTS, item.result.contents);
        }
        startActivity(intent);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        view.clearFocus();
    }
}
