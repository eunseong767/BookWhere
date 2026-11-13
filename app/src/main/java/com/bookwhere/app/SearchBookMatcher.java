package com.bookwhere.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class SearchBookMatcher {
    private static final Pattern ISBN_13 = Pattern.compile("[0-9]{13}");
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern BRACKETS = Pattern.compile("[\\p{Ps}\\p{Pe}<>]");
    private static final String EDITION = "리커버(?:판|에디션|특별판)?|양장(?:판|본)?|기프트에디션";
    private static final Pattern BRACKETED_EDITION = Pattern.compile(
            "(?:\\([^()]*(?:" + EDITION + ")[^()]*\\)|\\[[^\\[\\]]*(?:"
                    + EDITION + ")[^\\[\\]]*\\])$");
    private static final Pattern EDITION_SUFFIX = Pattern.compile(
            "(?:[:·-]?(?:" + EDITION + "))+$");
    private static final Pattern AUTHOR_SEPARATOR = Pattern.compile("[,;·/ㆍ]");

    private final Map<String, Book> byIsbn = new HashMap<>();
    private final Map<String, Book> byTitleAuthorPublisher = new HashMap<>();

    SearchBookMatcher(List<Book> books) {
        for (Book book : books) {
            String isbn = isbn13(book.getIsbn());
            String identity = identity(book.getTitle(), book.getAuthor(), normalize(book.getPublisher()));
            if (!isbn.isEmpty()) byIsbn.putIfAbsent(isbn, book);
            if (!identity.isEmpty()) byTitleAuthorPublisher.putIfAbsent(identity, book);
        }
    }

    Book match(SearchKakaoClient.Result result) {
        String publisher = normalize(result.publisher);
        Book byNumber = byIsbn.get(isbn13(result.isbn));
        if (byNumber != null) {
            String catalogPublisher = normalize(byNumber.getPublisher());
            if (publisher.isEmpty() || catalogPublisher.isEmpty() || publisher.equals(catalogPublisher)) {
                return byNumber;
            }
        }
        return byTitleAuthorPublisher.get(identity(result.title, result.author, publisher));
    }

    static List<SearchBookAdapter.Item> uniqueResults(
            List<SearchKakaoClient.Result> results, SearchBookMatcher matcher) {
        Map<String, SearchBookAdapter.Item> registered = new LinkedHashMap<>();
        Map<String, SearchBookAdapter.Item> external = new LinkedHashMap<>();
        for (SearchKakaoClient.Result result : results) {
            Book book = matcher == null ? null : matcher.match(result);
            SearchBookAdapter.Item item = new SearchBookAdapter.Item(book, result);
            if (book != null) {
                String isbn = isbn13(result.isbn);
                if (!isbn.isEmpty() && isbn.equals(isbn13(book.getIsbn()))) {
                    registered.put(book.getId(), item);
                } else {
                    registered.putIfAbsent(book.getId(), item);
                }
            } else {
                String publisher = normalize(result.publisher);
                String key = identity(result.title, result.author, publisher);
                if (key.isEmpty()) {
                    String isbn = isbn13(result.isbn);
                    key = isbn.isEmpty() ? "result:" + external.size() : "isbn:" + isbn + "\n" + publisher;
                }
                external.putIfAbsent(key, item);
            }
        }

        List<SearchBookAdapter.Item> items = new ArrayList<>(registered.values());
        items.addAll(external.values());
        return items;
    }

    static String isbn13(String value) {
        if (value == null) return "";
        for (String part : SPACES.split(value)) {
            String digits = part.replace("-", "");
            if (ISBN_13.matcher(digits).matches()) return digits;
        }
        return "";
    }

    private static String identity(String title, String author, String publisher) {
        String normalized = title == null ? "" : SPACES.matcher(
                Normalizer.normalize(title, Normalizer.Form.NFKC)).replaceAll("");
        String editionless = BRACKETED_EDITION.matcher(normalized).replaceFirst("");
        String titleKey = EDITION_SUFFIX.matcher(normalize(editionless).replace(",", "")).replaceFirst("");
        String authorKey = AUTHOR_SEPARATOR.matcher(normalize(author)).replaceAll("");
        if (titleKey.isEmpty() || authorKey.isEmpty() || publisher.isEmpty()) return "";
        return titleKey + "\n" + authorKey + "\n" + publisher;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        normalized = BRACKETS.matcher(normalized).replaceAll("");
        return SPACES.matcher(normalized).replaceAll("");
    }
}
