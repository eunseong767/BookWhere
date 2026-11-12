package com.bookwhere.app;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// B3 책 상세 — 하단 탭 없음
// books/{문서ID}를 불러와 정보와 플랫폼별 구독 여부를 보여주고,
// users/{uid}/wishlist/{문서ID}로 찜하기 / 찜 취소
public class DetailActivity extends AppCompatActivity {

    // 검색 화면에서 넘겨주는 책 문서 ID (예: "총_균_쇠")
    public static final String EXTRA_BOOK_ID = "bookId";

    private static final String TAG = "DetailActivity";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String bookId;
    private Book book;                 // 불러온 책 (불러오기 전에는 null)
    private DocumentReference wishRef; // users/{uid}/wishlist/{bookId}, 로그인 안 했으면 null
    private boolean isWished = false;
    private final Map<String, Long> monthlyPrices = new HashMap<>(); // plans/{플랫폼키}.monthlyPrice

    private ScrollView scroll;
    private ProgressBar progress;
    private MaterialCardView cardCover;
    private TextView tvCoverTitle;
    private TextView tvCoverAuthor;
    private TextView tvTitle;
    private TextView tvMeta;
    private TextView tvPublisher;
    private TextView tvPlatformCount;
    private TextView tvNone;
    private LinearLayout layoutPlatforms;
    private Button btnWish;
    private Button btnUnwish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        scroll = findViewById(R.id.scroll_detail);
        progress = findViewById(R.id.progress_detail);
        cardCover = findViewById(R.id.card_detail_cover);
        tvCoverTitle = findViewById(R.id.tv_detail_cover_title);
        tvCoverAuthor = findViewById(R.id.tv_detail_cover_author);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvMeta = findViewById(R.id.tv_detail_meta);
        tvPublisher = findViewById(R.id.tv_detail_publisher);
        tvPlatformCount = findViewById(R.id.tv_detail_platform_count);
        tvNone = findViewById(R.id.tv_detail_none);
        layoutPlatforms = findViewById(R.id.layout_detail_platforms);
        btnWish = findViewById(R.id.btn_detail_wish);
        btnUnwish = findViewById(R.id.btn_detail_unwish);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        if (TextUtils.isEmpty(bookId)) {
            Toast.makeText(this, "책 정보를 찾을 수 없어요.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            wishRef = db.collection("users").document(user.getUid())
                    .collection("wishlist").document(bookId);
        }

        btnWish.setOnClickListener(v -> addToWishlist());
        btnUnwish.setOnClickListener(v -> removeFromWishlist());

        loadBook();
        loadPlans();
    }

    /** books/{bookId} 불러오기 */
    private void loadBook() {
        db.collection("books").document(bookId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;

                    book = doc.toObject(Book.class);
                    if (book == null) { // 문서가 없음
                        Toast.makeText(this, "책 정보를 찾을 수 없어요.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    showBook();
                    loadWishState();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "books/" + bookId + " 불러오기 실패", e);
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(this, "책 정보를 불러오지 못했어요. 인터넷 연결을 확인해 주세요.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    /** plans 컬렉션에서 플랫폼별 월 가격 불러오기 (실패해도 화면은 그대로 보임) */
    private void loadPlans() {
        db.collection("plans")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        Long price = doc.getLong("monthlyPrice");
                        if (price != null) monthlyPrices.put(doc.getId(), price);
                    }
                    if (book != null) showPlatforms(); // 책이 먼저 떴으면 가격만 다시 채움
                })
                .addOnFailureListener(e -> Log.e(TAG, "plans 불러오기 실패", e));
    }

    /** 불러온 책 정보를 화면에 표시 */
    private void showBook() {
        // 표지 (검색 화면과 같은 색)
        int[] colors = SearchBookAdapter.coverColors(book.getId());
        int ink = ContextCompat.getColor(this, colors[1]);
        cardCover.setCardBackgroundColor(ContextCompat.getColor(this, colors[0]));
        tvCoverTitle.setTextColor(ink);
        tvCoverAuthor.setTextColor(ink);
        tvCoverTitle.setText(book.getTitle());
        tvCoverAuthor.setText(book.getAuthor());

        tvTitle.setText(orDash(book.getTitle()));
        tvMeta.setText(orDash(SearchBookAdapter.joinDot(book.getAuthor(), book.getCategory())));
        tvPublisher.setText("출판사 " + orDash(book.getPublisher()));

        showPlatforms();

        progress.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
    }

    /** 플랫폼 5개를 한 줄씩 붙임 */
    private void showPlatforms() {
        int total = Book.PLATFORM_KEYS.length;
        int available = SearchBookAdapter.availableCount(book);
        tvPlatformCount.setText(total + "곳 중 " + available + "곳");
        tvNone.setVisibility(book.isOnNothing() ? View.VISIBLE : View.GONE);

        layoutPlatforms.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String key : Book.PLATFORM_KEYS) {
            View row = inflater.inflate(R.layout.item_detail_platform, layoutPlatforms, false);
            ImageView ivIcon = row.findViewById(R.id.iv_detail_platform_icon);
            TextView tvName = row.findViewById(R.id.tv_detail_platform_name);
            TextView tvSide = row.findViewById(R.id.tv_detail_platform_side);

            tvName.setText(Book.platformName(key));

            if (book.isOn(key)) {
                ivIcon.setBackgroundResource(R.drawable.bg_detail_platform_on);
                ivIcon.setImageResource(R.drawable.ic_detail_check);
                ivIcon.setContentDescription("이용 가능");
                tvName.setTextColor(ContextCompat.getColor(this, R.color.text_main));
                tvName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

                Long price = monthlyPrices.get(key);
                tvSide.setText(price != null ? "월 " + won(price) : "이용 가능");
            } else {
                ivIcon.setBackgroundResource(R.drawable.bg_detail_platform_off);
                ivIcon.setImageResource(R.drawable.ic_detail_dash);
                ivIcon.setContentDescription("이용 불가");
                tvName.setTextColor(ContextCompat.getColor(this, R.color.text_sub));
                tvSide.setText("없음");
            }
            layoutPlatforms.addView(row);
        }
    }

    /** 이미 찜한 책인지 확인해서 버튼 바꾸기 */
    private void loadWishState() {
        if (wishRef == null) { // 로그인 안 함: 누르면 안내 메시지
            updateWishButtons();
            return;
        }
        wishRef.get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    isWished = doc.exists();
                    updateWishButtons();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "찜 여부 확인 실패", e);
                    if (isFinishing() || isDestroyed()) return;
                    updateWishButtons(); // 확인 못 하면 "찜하기"로 둠
                });
    }

    /** 찜하기: users/{uid}/wishlist/{bookId}에 저장 */
    private void addToWishlist() {
        if (wishRef == null) {
            Toast.makeText(this, "로그인 후 찜할 수 있어요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (book == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("title", book.getTitle());
        data.put("author", book.getAuthor());
        data.put("category", book.getCategory());
        data.put("addedAt", Timestamp.now());

        setWishButtonsEnabled(false); // 두 번 눌리지 않게
        wishRef.set(data)
                .addOnSuccessListener(unused -> {
                    if (isFinishing() || isDestroyed()) return;
                    isWished = true;
                    updateWishButtons();
                    Toast.makeText(this, "찜한 책에 담았어요", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "찜하기 실패", e);
                    if (isFinishing() || isDestroyed()) return;
                    updateWishButtons();
                    Toast.makeText(this, "찜하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
                });
    }

    /** 찜 취소: users/{uid}/wishlist/{bookId} 삭제 */
    private void removeFromWishlist() {
        if (wishRef == null) return;

        setWishButtonsEnabled(false);
        wishRef.delete()
                .addOnSuccessListener(unused -> {
                    if (isFinishing() || isDestroyed()) return;
                    isWished = false;
                    updateWishButtons();
                    Toast.makeText(this, "찜한 책에서 뺐어요", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "찜 취소 실패", e);
                    if (isFinishing() || isDestroyed()) return;
                    updateWishButtons();
                    Toast.makeText(this, "찜을 취소하지 못했어요. 다시 시도해 주세요.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    /** isWished에 맞게 "찜하기" / "찜 취소" 중 하나만 보이게 */
    private void updateWishButtons() {
        btnWish.setVisibility(isWished ? View.GONE : View.VISIBLE);
        btnUnwish.setVisibility(isWished ? View.VISIBLE : View.GONE);
        setWishButtonsEnabled(true);
    }

    private void setWishButtonsEnabled(boolean enabled) {
        btnWish.setEnabled(enabled);
        btnUnwish.setEnabled(enabled);
    }

    /** 11900 → "11,900원" */
    private static String won(long price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "원";
    }

    private static String orDash(String text) {
        return TextUtils.isEmpty(text) ? "-" : text;
    }
}
