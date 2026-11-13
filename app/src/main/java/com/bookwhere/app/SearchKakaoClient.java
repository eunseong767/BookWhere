package com.bookwhere.app;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class SearchKakaoClient {
    private static final String KEY_ASSET = "search_kakao_key.txt";
    private static final int PAGE_SIZE = 30;
    private static final int MAX_PAGE = 50;

    static Page search(Context context, String query, String target, int page)
            throws IOException, JSONException {
        String key;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(KEY_ASSET), StandardCharsets.UTF_8))) {
            key = reader.readLine();
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IOException("카카오 검색 키가 비어 있어요.");
        }

        String url = "https://dapi.kakao.com/v3/search/book?query="
                + URLEncoder.encode(query, "UTF-8") + "&page=" + page + "&size=" + PAGE_SIZE;
        if (target != null) url += "&target=" + target;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "KakaoAK " + key.trim());
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("카카오 책 검색 HTTP " + status);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("카카오 책 검색이 취소됐어요.");
                    }
                    response.append(line);
                }
                JSONObject body = new JSONObject(response.toString());
                JSONArray documents = body.getJSONArray("documents");
                List<Result> results = new ArrayList<>();
                for (int i = 0; i < documents.length(); i++) {
                    results.add(new Result(documents.getJSONObject(i)));
                }
                boolean hasMore = page < MAX_PAGE && documents.length() > 0
                        && !body.getJSONObject("meta").getBoolean("is_end");
                return new Page(results, hasMore);
            }
        } finally {
            connection.disconnect();
        }
    }

    static final class Page {
        final List<Result> results;
        final boolean hasMore;

        Page(List<Result> results, boolean hasMore) {
            this.results = results;
            this.hasMore = hasMore;
        }
    }

    static final class Result {
        final String title;
        final String author;
        final String publisher;
        final String isbn;
        final String thumbnail;
        final String contents;

        Result(JSONObject document) {
            this(document.optString("title"), readAuthor(document), document.optString("publisher"),
                    document.optString("isbn"),
                    document.optString("thumbnail"), document.optString("contents"));
        }

        Result(String title, String author, String publisher, String isbn, String thumbnail,
               String contents) {
            this.title = title;
            this.author = author;
            this.publisher = publisher;
            this.isbn = isbn;
            this.thumbnail = thumbnail;
            this.contents = contents;
        }

        private static String readAuthor(JSONObject document) {
            List<String> authors = new ArrayList<>();
            JSONArray array = document.optJSONArray("authors");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String name = array.optString(i).trim();
                    if (!name.isEmpty()) authors.add(name);
                }
            }
            return TextUtils.join(", ", authors);
        }
    }
}
