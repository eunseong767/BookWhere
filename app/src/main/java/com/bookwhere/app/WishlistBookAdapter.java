package com.bookwhere.app;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * B4 찜 목록 어댑터 (담당: 고은성)
 * rows 안에는 String(분야 제목) 또는 Book(책)이 섞여 들어옴.
 */
public class WishlistBookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onOpen(Book book);    // 책 눌렀을 때 → 상세 화면
        void onRemove(Book book);  // 하트 눌렀을 때 → 찜 취소
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BOOK = 1;

    // 표지 대신 쓰는 색깔 (책 제목에 따라 하나 고름)
    private static final int[] COVER_COLORS = {
            0xFF6E2A14, 0xFF4E3620, 0xFFB84A28, 0xFF6F4F32, 0xFF2B1F19
    };

    private final List<Object> rows = new ArrayList<>();
    private final Listener listener;

    public WishlistBookAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<Object> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_BOOK;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_wishlist_header, parent, false));
        }
        return new BookHolder(inflater.inflate(R.layout.item_wishlist_book, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText((String) row);
            return;
        }

        BookHolder h = (BookHolder) holder;
        Book book = (Book) row;
        String title = book.getTitle() == null ? "" : book.getTitle();

        // 표지 색깔 상자
        GradientDrawable cover = new GradientDrawable();
        cover.setColor(COVER_COLORS[Math.abs(title.hashCode()) % COVER_COLORS.length]);
        cover.setCornerRadius(6 * h.itemView.getResources().getDisplayMetrics().density);
        h.cover.setBackground(cover);
        h.cover.setText(title);

        h.title.setText(title);
        h.author.setText(book.getAuthor());

        // 구독 상태: "밀리의서재 외 2곳" / "구독 서비스에 없음"
        List<String> names = new ArrayList<>();
        for (String key : Book.PLATFORM_KEYS) {
            if (book.isOn(key)) names.add(Book.platformName(key));
        }
        if (names.isEmpty()) {
            h.status.setText("구독 서비스에 없음");
            h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.text_sub));
        } else {
            String text = names.get(0) + (names.size() > 1 ? " 외 " + (names.size() - 1) + "곳" : "");
            h.status.setText(text);
            h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.primary));
        }

        h.itemView.setOnClickListener(v -> listener.onOpen(book));
        h.heart.setOnClickListener(v -> listener.onRemove(book));
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View v) {
            super(v);
            text = v.findViewById(R.id.wishlist_header_text);
        }
    }

    static class BookHolder extends RecyclerView.ViewHolder {
        final TextView cover, title, author, status;
        final ImageButton heart;

        BookHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.wishlist_cover);
            title = v.findViewById(R.id.wishlist_title);
            author = v.findViewById(R.id.wishlist_author);
            status = v.findViewById(R.id.wishlist_status);
            heart = v.findViewById(R.id.wishlist_heart);
        }
    }
}
