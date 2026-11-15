package com.bookwhere.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 책 표지 이미지를 인터넷에서 받아 ImageView에 넣어주는 공통 클래스
 *
 * 쓰는 법:
 *   BookCoverLoader.load(imageView, book.getThumbnail());
 *
 * - 주소가 비었거나 실패하면 imageView를 숨김 (뒤에 있는 기본 책 모양이 보임)
 * - 한 번 받은 표지는 메모리에 저장해서 다시 받지 않음
 * - 목록에서 칸이 재사용돼도 다른 책 표지가 잘못 들어가지 않게 주소를 tag로 확인
 */
public final class BookCoverLoader {

    private static final String TAG = "BookCoverLoader";

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());
    // 표지 1장이 약 80KB라 100장 정도만 기억
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private BookCoverLoader() {
    }

    public static void load(ImageView view, String thumbnail) {
        String url = toHttps(thumbnail);
        view.setTag(R.id.tag_book_cover_url, url);
        view.setImageDrawable(null);
        view.setVisibility(View.GONE);
        if (url == null) return;

        Bitmap cached = cache.get(url);
        if (cached != null) {
            show(view, cached);
            return;
        }

        executor.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap != null) cache.put(url, bitmap);
            main.post(() -> {
                // 그 사이 이 칸에 다른 책이 들어왔으면 무시
                if (bitmap != null && url.equals(view.getTag(R.id.tag_book_cover_url))) {
                    show(view, bitmap);
                }
            });
        });
    }

    private static void show(ImageView view, Bitmap bitmap) {
        view.setImageBitmap(bitmap);
        view.setVisibility(View.VISIBLE);
    }

    private static String toHttps(String thumbnail) {
        if (TextUtils.isEmpty(thumbnail)) return null;
        if (thumbnail.startsWith("http://")) return "https://" + thumbnail.substring("http://".length());
        return thumbnail.startsWith("https://") ? thumbnail : null;
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (IOException e) {
            Log.w(TAG, "책 표지 불러오기 실패: " + url, e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
