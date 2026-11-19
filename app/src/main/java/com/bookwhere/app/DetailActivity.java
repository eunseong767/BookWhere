package com.bookwhere.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// B3 책 상세 — 하단 탭 없음
// books/{문서ID}를 불러와 정보와 플랫폼별 구독 여부를 보여주고,
// users/{uid}/wishlist/{문서ID}로 찜하기 / 찜 취소
public class DetailActivity extends AppCompatActivity {

    // 검색 화면에서 넘겨주는 책 문서 ID (예: "총_균_쇠")
    public static final String EXTRA_BOOK_ID = "bookId";
    static final String EXTRA_TITLE = "detailTitle";
    static final String EXTRA_AUTHOR = "detailAuthor";
    static final String EXTRA_PUBLISHER = "detailPublisher";
    static final String EXTRA_THUMBNAIL = "detailThumbnail";
    static final String EXTRA_CONTENTS = "detailContents";
    static final String EXTRA_ISBN = "detailIsbn";

    private static final String TAG = "DetailActivity";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor();

    private String bookId;
    private Book book;                 // 불러온 책 (불러오기 전에는 null)
    private DocumentReference wishRef; // users/{uid}/wishlist/{bookId}, 로그인 안 했으면 null
    private boolean isWished = false;
    private final Map<String, Long> monthlyPrices = new HashMap<>(); // plans/{플랫폼키}.monthlyPrice

    private ScrollView scroll;
    private ProgressBar progress;
    private MaterialCardView cardCover;
    private ImageView ivCover;
    private View coverPlaceholder;
    private TextView tvCoverTitle;
    private TextView tvCoverAuthor;
    private TextView tvTitle;
    private TextView tvMeta;
    private TextView tvPublisher;
    private TextView tvContents;
    private TextView tvPlatformCount;
    private TextView tvNone;
    private LinearLayout layoutPlatforms;
    private Button btnWish;
    private Button btnUnwish;
    private Button btnRequest;
    private TextView tvRequestCount;
    private DocumentReference requestRef; // requests/{ISBN 또는 제목_저자}, 조사하지 않은 책일 때만

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        scroll = findViewById(R.id.scroll_detail);
        progress = findViewById(R.id.progress_detail);
        cardCover = findViewById(R.id.card_detail_cover);
        ivCover = findViewById(R.id.iv_detail_cover);
        coverPlaceholder = findViewById(R.id.layout_detail_cover_placeholder);
        tvCoverTitle = findViewById(R.id.tv_detail_cover_title);
        tvCoverAuthor = findViewById(R.id.tv_detail_cover_author);
        tvTitle = findViewById(R.id.tv_detail_title);
        tvMeta = findViewById(R.id.tv_detail_meta);
        tvPublisher = findViewById(R.id.tv_detail_publisher);
        tvContents = findViewById(R.id.tv_detail_contents);
        tvPlatformCount = findViewById(R.id.tv_detail_platform_count);
        tvNone = findViewById(R.id.tv_detail_none);
        layoutPlatforms = findViewById(R.id.layout_detail_platforms);
        btnWish = findViewById(R.id.btn_detail_wish);
        btnUnwish = findViewById(R.id.btn_detail_unwish);
        btnRequest = findViewById(R.id.btn_detail_request);
        tvRequestCount = findViewById(R.id.tv_detail_request_count);

        bookId = getIntent().getStringExtra(EXTRA_BOOK_ID);
        if (TextUtils.isEmpty(bookId)) {
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            if (TextUtils.isEmpty(title)) {
                Toast.makeText(this, "책 정보를 찾을 수 없어요.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            showExternalBook(title);
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
                    if (getIntent().hasExtra(EXTRA_TITLE)) showSearchMetadata();
                    else loadCatalogMetadata();
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
        showPlaceholder(book.getId(), book.getTitle(), book.getAuthor());
        tvTitle.setText(orDash(book.getTitle()));
        tvMeta.setText(orDash(SearchBookAdapter.joinDot(book.getAuthor(), book.getCategory())));
        tvPublisher.setText("출판사 " + orDash(book.getPublisher()));

        showPlatforms();

        showContent();
    }

    private void showExternalBook(String title) {
        String author = getIntent().getStringExtra(EXTRA_AUTHOR);
        showPlaceholder(title, title, author);
        tvTitle.setText(title);
        tvMeta.setText(author);
        String publisher = getIntent().getStringExtra(EXTRA_PUBLISHER);
        if (TextUtils.isEmpty(publisher)) {
            tvPublisher.setVisibility(View.GONE);
        } else {
            tvPublisher.setText("출판사 " + publisher);
        }
        findViewById(R.id.layout_detail_platform_heading).setVisibility(View.GONE);
        findViewById(R.id.card_detail_platforms).setVisibility(View.GONE);
        setWishButtonsEnabled(false);
        btnWish.setAlpha(0.55f);
        setupResearchRequest(title, author);
        showSearchMetadata();
        showContent();
    }

    /** "구독 정보 준비 중" 카드: 조사 요청 버튼 준비, 이미 요청했으면 "요청 완료"로 */
    private void setupResearchRequest(String title, String author) {
        String isbn = SearchBookMatcher.isbn13(getIntent().getStringExtra(EXTRA_ISBN));
        String requestId = isbn.isEmpty() ? requestIdFromTitle(title, author) : isbn;
        requestRef = db.collection("requests").document(requestId);

        findViewById(R.id.layout_detail_request).setVisibility(View.VISIBLE);
        btnRequest.setOnClickListener(v -> sendResearchRequest(title, author, isbn));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return; // 누르면 로그인 안내
        requestRef.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(mine -> {
                    if (!mine.exists()) return;
                    requestRef.get().addOnSuccessListener(doc -> {
                        if (isFinishing() || isDestroyed()) return;
                        Long count = doc.getLong("count");
                        showRequestDone(count == null ? 1 : count);
                    });
                })
                .addOnFailureListener(e -> Log.w(TAG, "조사 요청 여부 확인 실패", e));
    }

    /**
     * 조사 요청: requests/{id}의 count를 1 올리고 requests/{id}/users/{uid}에 기록.
     * 같은 사용자가 이미 요청했으면 count는 그대로 (트랜잭션으로 한 번에 확인·저장)
     */
    private void sendResearchRequest(String title, String author, String isbn) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인이 필요해요", Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentReference mineRef = requestRef.collection("users").document(user.getUid());

        btnRequest.setEnabled(false); // 두 번 눌리지 않게
        db.runTransaction(transaction -> {
                    DocumentSnapshot request = transaction.get(requestRef);
                    DocumentSnapshot mine = transaction.get(mineRef);
                    Long saved = request.getLong("count");
                    long count = saved == null ? 0 : saved;
                    if (mine.exists()) return count; // 이미 요청함

                    Map<String, Object> data = new HashMap<>();
                    data.put("title", title);
                    data.put("author", author == null ? "" : author);
                    data.put("isbn", isbn);
                    data.put("count", count + 1);
                    data.put("lastRequestedAt", FieldValue.serverTimestamp());
                    transaction.set(requestRef, data, SetOptions.merge());

                    Map<String, Object> mineData = new HashMap<>();
                    mineData.put("requestedAt", FieldValue.serverTimestamp());
                    transaction.set(mineRef, mineData);
                    return count + 1;
                })
                .addOnSuccessListener(count -> {
                    if (isFinishing() || isDestroyed()) return;
                    showRequestDone(count);
                    Toast.makeText(this, "조사 요청을 보냈어요", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "조사 요청 실패", e);
                    if (isFinishing() || isDestroyed()) return;
                    btnRequest.setEnabled(true);
                    Toast.makeText(this, "요청하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showRequestDone(long count) {
        btnRequest.setEnabled(false);
        btnRequest.setText("요청 완료");
        tvRequestCount.setText(count + "명이 요청했어요");
        tvRequestCount.setVisibility(View.VISIBLE);
    }

    /** ISBN이 없는 책: "제목_저자"에서 공백·특수문자를 _로 바꿔 문서 ID로 씀 */
    private static String requestIdFromTitle(String title, String author) {
        String raw = title + "_" + (author == null ? "" : author);
        String id = raw.replaceAll("[^0-9A-Za-z가-힣]+", "_").replaceAll("^_+|_+$", "");
        return id.isEmpty() ? "unknown" : id;
    }

    private void showPlaceholder(String id, String title, String author) {
        int[] colors = SearchBookAdapter.coverColors(id);
        int ink = ContextCompat.getColor(this, colors[1]);
        cardCover.setCardBackgroundColor(ContextCompat.getColor(this, colors[0]));
        tvCoverTitle.setTextColor(ink);
        tvCoverAuthor.setTextColor(ink);
        tvCoverTitle.setText(title);
        tvCoverAuthor.setText(author);
    }

    private void showContent() {
        progress.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
    }

    private void showSearchMetadata() {
        showDescription(getIntent().getStringExtra(EXTRA_CONTENTS));
        String thumbnail = getIntent().getStringExtra(EXTRA_THUMBNAIL);
        if (!TextUtils.isEmpty(thumbnail)) {
            mediaExecutor.execute(() -> showCoverWhenReady(downloadCover(thumbnail)));
        }
    }

    private void loadCatalogMetadata() {
        Context context = getApplicationContext();
        Book catalogBook = book;
        tvContents.setText("책 소개를 불러오는 중이에요.");
        mediaExecutor.execute(() -> {
            SearchKakaoClient.Result result = null;
            try {
                result = findMetadata(context, catalogBook);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "카카오 도서 정보 불러오기 실패", e);
            }
            SearchKakaoClient.Result metadata = result;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showDescription(metadata == null ? null : metadata.contents);
                }
            });
            if (metadata != null && !metadata.thumbnail.isEmpty()
                    && !Thread.currentThread().isInterrupted()) {
                showCoverWhenReady(downloadCover(metadata.thumbnail));
            }
        });
    }

    private static SearchKakaoClient.Result findMetadata(Context context, Book book)
            throws IOException, JSONException {
        SearchBookMatcher matcher = new SearchBookMatcher(Collections.singletonList(book));
        String isbn = SearchBookMatcher.isbn13(book.getIsbn());
        if (!isbn.isEmpty()) {
            for (SearchKakaoClient.Result result : SearchKakaoClient.search(context, isbn, "isbn", 1).results) {
                if (isbn.equals(SearchBookMatcher.isbn13(result.isbn)) && matcher.match(result) != null) {
                    return result;
                }
            }
        }
        if (TextUtils.isEmpty(book.getTitle()) || Thread.currentThread().isInterrupted()) return null;
        for (SearchKakaoClient.Result result : SearchKakaoClient.search(context, book.getTitle(), "title", 1).results) {
            if (matcher.match(result) != null) return result;
        }
        return null;
    }

    private void showDescription(String contents) {
        String preview = DetailBookDescription.completeSentences(contents);
        tvContents.setText(preview.isEmpty() ? getString(R.string.detail_no_description) : preview);
    }

    private static Bitmap downloadCover(String thumbnail) {
        String address = thumbnail.startsWith("http://")
                ? "https://" + thumbnail.substring("http://".length()) : thumbnail;
        if (!address.startsWith("https://")) return null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            try {
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (InputStream stream = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(stream);
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            Log.w(TAG, "책 표지 불러오기 실패", e);
            return null;
        }
    }

    private void showCoverWhenReady(Bitmap cover) {
        if (cover == null) return;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            ivCover.setImageBitmap(cover);
            coverPlaceholder.setVisibility(View.GONE);
            ivCover.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onDestroy() {
        mediaExecutor.shutdownNow();
        super.onDestroy();
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
        if (book == null) return;
        if (wishRef == null) {
            Toast.makeText(this, "로그인 후 찜할 수 있어요.", Toast.LENGTH_SHORT).show();
            return;
        }
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
        if (book == null || wishRef == null) return;

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
