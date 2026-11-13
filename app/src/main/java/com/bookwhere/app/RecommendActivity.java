package com.bookwhere.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * B6 구독 추천 (담당: 고은성) — 하단 탭 없음, 찜한 책 화면의 버튼으로 들어옴
 *
 * 계산 방법
 * 1. 찜한 책마다 books의 platforms를 보고, 요금제(plans)별로 읽을 수 있는 책을 셈
 * 2. sam 프리미엄처럼 monthlyLimit이 있으면 그 권수까지만 인정 (readable)
 * 3. 읽을 수 있는 권수가 많은 순 → 같으면 가격이 싼 순으로 정렬, 1위가 '추천'
 * 4. 1권당 가격(월 가격 ÷ 읽을 수 있는 권수)이 가장 싼 요금제에 '권당 최저' 표시
 */
public class RecommendActivity extends AppCompatActivity {

    /** 요금제 한 줄 (비교 카드 1개) */
    public static class PlanRow {
        String key;                       // 플랫폼 키 (millie 등)
        String name;                      // 요금제 이름
        long price;                       // 월 가격
        Long limit;                       // 월 권수 제한 (무제한이면 null)
        List<String> covered = new ArrayList<>(); // 읽을 수 있는 찜한 책 제목
        List<String> missed = new ArrayList<>();  // 없는 찜한 책 제목
        int count;                        // 이 플랫폼에 있는 찜한 책 수
        int readable;                     // 실제로 읽을 수 있는 수 (권수 제한 적용)
        boolean isBest;
        boolean isLowest;

        long perBook() {
            return readable == 0 ? 0 : Math.round((double) price / readable);
        }
    }

    // "구독하러 가기" 눌렀을 때 열 사이트
    private static final Map<String, String> PLATFORM_URLS = new HashMap<>();

    static {
        PLATFORM_URLS.put(Book.MILLIE, "https://www.millie.co.kr");
        PLATFORM_URLS.put(Book.RIDI, "https://select.ridibooks.com");
        PLATFORM_URLS.put(Book.CREMA, "https://cremaclub.yes24.com");
        PLATFORM_URLS.put(Book.SAM_UNLIMITED, "https://sam.kyobobook.co.kr");
        PLATFORM_URLS.put(Book.SAM_PREMIUM, "https://sam.kyobobook.co.kr");
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private View content;
    private TextView message;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        content = findViewById(R.id.recommend_content);
        message = findViewById(R.id.recommend_message);
        progress = findViewById(R.id.recommend_progress);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        load();
    }

    private void load() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showMessage("로그인이 필요해요.");
            return;
        }

        Task<QuerySnapshot> wishTask = db.collection("users").document(user.getUid())
                .collection("wishlist").get();
        Task<QuerySnapshot> booksTask = db.collection("books").get();
        Task<QuerySnapshot> plansTask = db.collection("plans").get();

        Tasks.whenAllSuccess(wishTask, booksTask, plansTask)
                .addOnSuccessListener(results -> {
                    if (isFinishing()) return;

                    // 책 전체를 문서ID로 찾을 수 있게
                    Map<String, Book> booksById = new HashMap<>();
                    for (DocumentSnapshot doc : ((QuerySnapshot) results.get(1)).getDocuments()) {
                        Book b = doc.toObject(Book.class);
                        if (b != null) booksById.put(doc.getId(), b);
                    }

                    // 찜한 책
                    List<Book> wished = new ArrayList<>();
                    for (DocumentSnapshot doc : ((QuerySnapshot) results.get(0)).getDocuments()) {
                        Book b = booksById.get(doc.getId());
                        if (b != null) wished.add(b);
                    }
                    if (wished.isEmpty()) {
                        showMessage("찜한 책이 있어야 구독을 추천해 드릴 수 있어요.");
                        return;
                    }

                    List<PlanRow> rows = calculate(wished, ((QuerySnapshot) results.get(2)).getDocuments());
                    showResult(wished, rows);
                })
                .addOnFailureListener(e -> showMessage("추천 정보를 불러오지 못했어요.\n" + e.getMessage()));
    }

    /** 추천 계산 */
    private List<PlanRow> calculate(List<Book> wished, List<DocumentSnapshot> planDocs) {
        List<PlanRow> rows = new ArrayList<>();

        for (DocumentSnapshot doc : planDocs) {
            PlanRow row = new PlanRow();
            row.key = doc.getString("platformKey");
            if (row.key == null) row.key = doc.getId();
            row.name = doc.getString("name");
            Long price = doc.getLong("monthlyPrice");
            row.price = price == null ? 0 : price;
            row.limit = doc.getLong("monthlyLimit"); // 무제한이면 null

            for (Book b : wished) {
                if (b.isOn(row.key)) row.covered.add(b.getTitle());
                else row.missed.add(b.getTitle());
            }
            row.count = row.covered.size();
            row.readable = row.limit == null ? row.count : (int) Math.min(row.count, row.limit);
            rows.add(row);
        }

        // 읽을 수 있는 권수 많은 순 → 가격 싼 순
        Collections.sort(rows, (a, b) -> {
            if (a.readable != b.readable) return b.readable - a.readable;
            return Long.compare(a.price, b.price);
        });

        if (!rows.isEmpty() && rows.get(0).readable > 0) rows.get(0).isBest = true;

        // 1권당 가격 최저
        long lowest = Long.MAX_VALUE;
        for (PlanRow r : rows) {
            if (r.readable > 0) lowest = Math.min(lowest, r.perBook());
        }
        for (PlanRow r : rows) {
            r.isLowest = r.readable > 0 && r.perBook() == lowest;
        }
        return rows;
    }

    private void showResult(List<Book> wished, List<PlanRow> rows) {
        int wishCount = wished.size();
        PlanRow best = rows.isEmpty() ? null : rows.get(0);

        View bestCard = findViewById(R.id.recommend_best_card);
        View noCover = findViewById(R.id.recommend_no_cover);

        if (best != null && best.isBest) {
            bestCard.setVisibility(View.VISIBLE);
            noCover.setVisibility(View.GONE);

            ((TextView) findViewById(R.id.best_name)).setText(best.name);
            String desc = "찜한 " + wishCount + "권 중 " + best.readable + "권을 읽을 수 있어요";
            if (best.limit != null && best.count > best.limit) {
                desc += "\n(이 요금제는 한 달에 " + best.limit + "권까지예요)";
            }
            ((TextView) findViewById(R.id.best_desc)).setText(desc);
            ((TextView) findViewById(R.id.best_price)).setText("월 " + won(best.price));
            ((TextView) findViewById(R.id.best_per_book)).setText("월 " + won(best.perBook()));

            Button subscribe = findViewById(R.id.btn_subscribe);
            String url = PLATFORM_URLS.get(best.key);
            subscribe.setOnClickListener(v -> {
                if (url != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            });
        } else {
            bestCard.setVisibility(View.GONE);
            noCover.setVisibility(View.VISIBLE);
        }

        // 요금제 비교
        RecyclerView recycler = findViewById(R.id.recommend_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new RecommendPlanAdapter(rows, wishCount));

        // 어떤 구독에도 없는 책
        LinearLayout list = findViewById(R.id.recommend_uncovered_list);
        list.removeAllViews();
        int uncovered = 0;
        for (Book b : wished) {
            if (b.isOnNothing()) {
                list.addView(uncoveredCard(b));
                uncovered++;
            }
        }
        findViewById(R.id.recommend_uncovered_section)
                .setVisibility(uncovered > 0 ? View.VISIBLE : View.GONE);

        progress.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    /** 구독에 없는 책 카드 (베이지 박스) */
    private View uncoveredCard(Book b) {
        float d = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.recommend_bg_uncovered);
        int pad = (int) (12 * d);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(b.getTitle());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.soft_text));
        card.addView(title);

        TextView author = new TextView(this);
        author.setText(b.getAuthor());
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        author.setTextColor(ContextCompat.getColor(this, R.color.soft_text));
        card.addView(author);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * d);
        card.setLayoutParams(lp);
        return card;
    }

    private void showMessage(String text) {
        progress.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    /** 11900 → "11,900원" */
    static String won(long amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
