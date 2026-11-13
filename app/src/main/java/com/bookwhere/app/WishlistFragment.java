package com.bookwhere.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4 찜한 책 (담당: 고은성)
 * users/{uid}/wishlist 를 불러와서 분야별로 묶어 보여줌.
 * 책의 플랫폼 정보는 books 컬렉션에서 가져옴.
 */
public class WishlistFragment extends Fragment {

    // ===== 테스트용 (로그인 화면 완성 전까지만) =====
    // TEST_MODE = true 이고 로그인이 안 돼 있으면 아래 테스트 계정으로 자동 로그인,
    // 찜 목록이 비어 있으면 TEST_BOOK_IDS 책들을 찜에 넣어줌.
    // Firebase 콘솔 > Authentication 에서 테스트 계정 만들고 이메일/비번 적기.
    // PR 올리기 전에 TEST_MODE = false 로 바꾸기!
    private static final boolean TEST_MODE = false;
    private static final String TEST_EMAIL = "";
    private static final String TEST_PASSWORD = "";
    private static final String[] TEST_BOOK_IDS = {
            "트렌드_코리아_2026", "총_균_쇠", "사탄탱고", "프로젝트_헤일메리", "돈의_속성", "7년의_밤", "모순"
    };
    private boolean testSeeded = false;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private RecyclerView recycler;
    private View emptyView, bottomBar;
    private TextView countText, messageText;
    private ProgressBar progress;
    private WishlistBookAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wishlist, container, false);

        recycler = view.findViewById(R.id.wishlist_recycler);
        emptyView = view.findViewById(R.id.wishlist_empty);
        bottomBar = view.findViewById(R.id.wishlist_bottom_bar);
        countText = view.findViewById(R.id.wishlist_count);
        messageText = view.findViewById(R.id.wishlist_message);
        progress = view.findViewById(R.id.wishlist_progress);

        adapter = new WishlistBookAdapter(new WishlistBookAdapter.Listener() {
            @Override
            public void onOpen(Book book) {
                Intent intent = new Intent(requireContext(), DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_BOOK_ID, book.getId());
                startActivity(intent);
            }

            @Override
            public void onRemove(Book book) {
                removeFromWishlist(book);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        // 비어 있을 때 "책 찾으러 가기" → 검색 탭으로
        view.findViewById(R.id.btn_go_search).setOnClickListener(v -> {
            BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_nav);
            if (nav != null) nav.setSelectedItemId(R.id.nav_search);
        });

        // "구독 추천 보기" → B6
        view.findViewById(R.id.btn_recommend).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RecommendActivity.class)));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 상세 화면에서 찜/찜 취소하고 돌아와도 바로 반영되도록 매번 새로 불러옴
        loadWishlist();
    }

    private void loadWishlist() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (TEST_MODE && !TEST_EMAIL.isEmpty()) {
                showLoading();
                FirebaseAuth.getInstance().signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD)
                        .addOnSuccessListener(r -> loadWishlist())
                        .addOnFailureListener(e -> showMessage("테스트 로그인 실패: " + e.getMessage()));
                return;
            }
            showMessage("로그인이 필요해요.");
            return;
        }

        showLoading();
        String uid = user.getUid();

        // 책 전체(플랫폼 정보용) + 내 찜 목록을 같이 불러옴
        Task<QuerySnapshot> booksTask = db.collection("books").get();
        Task<QuerySnapshot> wishTask = wishlistRef(uid)
                .orderBy("addedAt", Query.Direction.DESCENDING).get();

        Tasks.whenAllSuccess(booksTask, wishTask)
                .addOnSuccessListener(results -> {
                    if (!isAdded()) return;

                    Map<String, Book> booksById = new HashMap<>();
                    for (DocumentSnapshot doc : ((QuerySnapshot) results.get(0)).getDocuments()) {
                        Book b = doc.toObject(Book.class);
                        if (b != null) booksById.put(doc.getId(), b);
                    }

                    QuerySnapshot wishSnap = (QuerySnapshot) results.get(1);
                    if (wishSnap.isEmpty() && TEST_MODE && !testSeeded) {
                        seedTestWishlist(uid, booksById);
                        return;
                    }

                    List<Book> wished = new ArrayList<>();
                    for (DocumentSnapshot doc : wishSnap.getDocuments()) {
                        Book b = booksById.get(doc.getId());
                        if (b == null) {
                            // books에 없는 책이면 찜에 저장된 정보만으로 표시
                            b = new Book();
                            b.setId(doc.getId());
                            b.setTitle(doc.getString("title"));
                            b.setAuthor(doc.getString("author"));
                            b.setCategory(doc.getString("category"));
                        }
                        wished.add(b);
                    }
                    showBooks(wished);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) showMessage("찜 목록을 불러오지 못했어요.\n" + e.getMessage());
                });
    }

    /** 분야별로 묶어서 화면에 표시 (최근에 찜한 책의 분야가 위로) */
    private void showBooks(List<Book> wished) {
        progress.setVisibility(View.GONE);
        messageText.setVisibility(View.GONE);

        if (wished.isEmpty()) {
            countText.setText("");
            recycler.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        Map<String, List<Book>> byCategory = new LinkedHashMap<>();
        for (Book b : wished) {
            String cat = b.getCategory() == null || b.getCategory().isEmpty() ? "기타" : b.getCategory();
            if (!byCategory.containsKey(cat)) byCategory.put(cat, new ArrayList<>());
            byCategory.get(cat).add(b);
        }

        List<Object> rows = new ArrayList<>();
        for (Map.Entry<String, List<Book>> entry : byCategory.entrySet()) {
            rows.add(entry.getKey() + "  " + entry.getValue().size() + "권");
            rows.addAll(entry.getValue());
        }

        countText.setText(wished.size() + "권");
        adapter.setRows(rows);
        emptyView.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
    }

    private void removeFromWishlist(Book book) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || book.getId() == null) return;

        wishlistRef(user.getUid()).document(book.getId()).delete()
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "찜을 취소했어요", Toast.LENGTH_SHORT).show();
                    loadWishlist();
                })
                .addOnFailureListener(e -> {
                    if (isAdded())
                        Toast.makeText(requireContext(), "찜 취소에 실패했어요", Toast.LENGTH_SHORT).show();
                });
    }

    /** 테스트용: 찜 목록에 책 몇 권 넣기 */
    private void seedTestWishlist(String uid, Map<String, Book> booksById) {
        testSeeded = true;
        WriteBatch batch = db.batch();
        for (String id : TEST_BOOK_IDS) {
            Book b = booksById.get(id);
            if (b == null) continue;
            Map<String, Object> data = new HashMap<>();
            data.put("title", b.getTitle());
            data.put("author", b.getAuthor());
            data.put("category", b.getCategory());
            data.put("addedAt", FieldValue.serverTimestamp());
            batch.set(wishlistRef(uid).document(id), data);
        }
        batch.commit()
                .addOnSuccessListener(v -> loadWishlist())
                .addOnFailureListener(e -> {
                    if (isAdded()) showMessage("테스트 데이터 넣기 실패: " + e.getMessage());
                });
    }

    private CollectionReference wishlistRef(String uid) {
        return db.collection("users").document(uid).collection("wishlist");
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        messageText.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }

    private void showMessage(String message) {
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        countText.setText("");
        messageText.setText(message);
        messageText.setVisibility(View.VISIBLE);
    }
}
