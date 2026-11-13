package com.bookwhere.app;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * B6 요금제 비교 카드 목록 (담당: 고은성)
 * 카드를 누르면 그 요금제로 읽을 수 있는 책(✓)과 없는 책(✕)이 펼쳐짐.
 */
public class RecommendPlanAdapter extends RecyclerView.Adapter<RecommendPlanAdapter.Holder> {

    private final List<RecommendActivity.PlanRow> rows;
    private final int wishCount;
    private int expandedPosition = 0; // 처음엔 1위 카드만 펼침

    public RecommendPlanAdapter(List<RecommendActivity.PlanRow> rows, int wishCount) {
        this.rows = rows;
        this.wishCount = wishCount;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommend_plan, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RecommendActivity.PlanRow row = rows.get(position);
        Context ctx = h.itemView.getContext();

        h.name.setText((position + 1) + ". " + row.name);
        h.badgeBest.setVisibility(row.isBest ? View.VISIBLE : View.GONE);
        h.badgeLowest.setVisibility(row.isLowest ? View.VISIBLE : View.GONE);

        h.price.setText("월 " + RecommendActivity.won(row.price));
        h.perBook.setText(row.readable > 0
                ? "1권당 월 " + RecommendActivity.won(row.perBook())
                : "읽을 수 있는 책 없음");

        h.bar.setProgress(wishCount == 0 ? 0 : row.count * 100 / wishCount);
        h.count.setText("찜한 책 " + row.count + "/" + wishCount + "권");

        if (row.limit != null) {
            h.limitNote.setVisibility(View.VISIBLE);
            h.limitNote.setText("한 달에 " + row.limit + "권까지 읽을 수 있는 요금제예요");
        } else {
            h.limitNote.setVisibility(View.GONE);
        }

        // 펼친 상태면 책 목록 채우기
        boolean expanded = position == expandedPosition;
        h.chevron.setRotation(expanded ? 180f : 0f);
        h.detail.removeAllViews();
        if (expanded) {
            for (String title : row.covered) h.detail.addView(bookLine(ctx, title, true));
            for (String title : row.missed) h.detail.addView(bookLine(ctx, title, false));
            h.detail.setVisibility(View.VISIBLE);
        } else {
            h.detail.setVisibility(View.GONE);
        }

        h.header.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int old = expandedPosition;
            expandedPosition = (pos == expandedPosition) ? -1 : pos;
            if (old >= 0) notifyItemChanged(old);
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /** ✓ 책 제목 / ✕ 책 제목 한 줄 만들기 */
    private View bookLine(Context ctx, String title, boolean covered) {
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, (int) (4 * d), 0, (int) (4 * d));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(covered ? R.drawable.recommend_ic_check : R.drawable.recommend_ic_x);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.addView(icon, new LinearLayout.LayoutParams((int) (18 * d), (int) (18 * d)));

        TextView text = new TextView(ctx);
        text.setText(covered ? title : title + " (없음)");
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTextColor(ContextCompat.getColor(ctx, covered ? R.color.text_main : R.color.text_sub));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart((int) (8 * d));
        line.addView(text, lp);

        return line;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View header;
        final TextView name, badgeBest, badgeLowest, price, perBook, count, limitNote;
        final ImageView chevron;
        final ProgressBar bar;
        final LinearLayout detail;

        Holder(View v) {
            super(v);
            header = v.findViewById(R.id.plan_header);
            name = v.findViewById(R.id.plan_name);
            badgeBest = v.findViewById(R.id.plan_badge_best);
            badgeLowest = v.findViewById(R.id.plan_badge_lowest);
            chevron = v.findViewById(R.id.plan_chevron);
            price = v.findViewById(R.id.plan_price);
            perBook = v.findViewById(R.id.plan_per_book);
            bar = v.findViewById(R.id.plan_bar);
            count = v.findViewById(R.id.plan_count);
            limitNote = v.findViewById(R.id.plan_limit_note);
            detail = v.findViewById(R.id.plan_detail);
        }
    }
}
