package com.bookwhere.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

// B2 책 검색 결과 목록 — 항목 모양은 item_search_book.xml
public class SearchBookAdapter extends RecyclerView.Adapter<SearchBookAdapter.ViewHolder> {

    /** 항목을 눌렀을 때 SearchFragment로 알려줌 */
    public interface OnBookClickListener {
        void onBookClick(Item item);
    }

    static final class Item {
        final Book book;
        final SearchKakaoClient.Result result;

        Item(Book book, SearchKakaoClient.Result result) {
            this.book = book;
            this.result = result;
        }
    }

    // 표지 색 {배경, 글자} — colors.xml에 있는 색만 사용
    private static final int[][] COVER_COLORS = {
            {R.color.primary, R.color.surface},
            {R.color.text_main, R.color.nav_bg},
            {R.color.nav_selected, R.color.text_main},
            {R.color.text_sub, R.color.surface},
            {R.color.border, R.color.text_main},
    };

    private final List<Item> books = new ArrayList<>();
    private final OnBookClickListener listener;

    public SearchBookAdapter(OnBookClickListener listener) {
        this.listener = listener;
    }

    /** 보여줄 책 목록을 통째로 바꿈 (검색할 때마다 호출) */
    @SuppressLint("NotifyDataSetChanged")
    public void setBooks(List<Item> newBooks) {
        books.clear();
        books.addAll(newBooks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_book, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = books.get(position);
        Book book = item.book;
        SearchKakaoClient.Result result = item.result;
        String title = result == null ? book.getTitle() : result.title;
        String author = result == null ? book.getAuthor() : result.author;
        Context context = holder.itemView.getContext();

        // 표지
        int[] colors = coverColors(book == null ? title : book.getId());
        holder.cover.setCardBackgroundColor(ContextCompat.getColor(context, colors[0]));
        holder.tvCoverTitle.setTextColor(ContextCompat.getColor(context, colors[1]));
        holder.tvCoverAuthor.setTextColor(ContextCompat.getColor(context, colors[1]));
        holder.tvCoverTitle.setText(title);
        holder.tvCoverAuthor.setText(author);
        // 표지 사진: 카카오 검색 결과의 표지, 없으면 81권에 저장된 표지
        String thumbnail = result != null && !TextUtils.isEmpty(result.thumbnail) ? result.thumbnail
                : book != null ? book.getThumbnail() : null;
        BookCoverLoader.load(holder.ivCover, thumbnail);

        // 표지 아래 글자
        holder.tvTitle.setText(title);
        String metadata = result == null ? joinDot(author, book.getCategory())
                : joinDot(result.publisher, author);
        holder.tvMeta.setText(metadata);

        if (result != null) {
            holder.tvStatus.setVisibility(book == null ? View.GONE : View.VISIBLE);
            holder.tvStatus.setText("구독 정보 있음");
            holder.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.primary));
        } else {
            int count = availableCount(book);
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(count > 0 ? count + "곳에서 이용 가능" : "구독 서비스에 없어요");
            holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                    count > 0 ? R.color.primary : R.color.text_sub));
        }

        holder.itemView.setOnClickListener(v -> listener.onBookClick(item));
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    /** 책마다 늘 같은 표지 색 {배경, 글자} (B3 상세 화면에서도 같이 씀) */
    public static int[] coverColors(String bookId) {
        int hash = bookId == null ? 0 : bookId.hashCode();
        return COVER_COLORS[Math.floorMod(hash, COVER_COLORS.length)];
    }

    /** 읽을 수 있는 구독 플랫폼 수 */
    public static int availableCount(Book book) {
        int count = 0;
        for (String key : Book.PLATFORM_KEYS) {
            if (book.isOn(key)) count++;
        }
        return count;
    }

    /** "저자 · 분야" (비어 있는 값은 빼고) */
    public static String joinDot(String a, String b) {
        if (TextUtils.isEmpty(a)) return b == null ? "" : b;
        if (TextUtils.isEmpty(b)) return a;
        return a + " · " + b;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cover;
        final ImageView ivCover;
        final TextView tvCoverTitle;
        final TextView tvCoverAuthor;
        final TextView tvTitle;
        final TextView tvMeta;
        final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.card_search_book_cover);
            ivCover = itemView.findViewById(R.id.iv_search_book_cover);
            tvCoverTitle = itemView.findViewById(R.id.tv_search_book_cover_title);
            tvCoverAuthor = itemView.findViewById(R.id.tv_search_book_cover_author);
            tvTitle = itemView.findViewById(R.id.tv_search_book_title);
            tvMeta = itemView.findViewById(R.id.tv_search_book_meta);
            tvStatus = itemView.findViewById(R.id.tv_search_book_status);
        }
    }
}
