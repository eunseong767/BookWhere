package com.bookwhere.app;

import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

/**
 * 책 한 권 (Firestore books 컬렉션 문서 1개)
 *
 * 불러오는 법:
 *   Book book = documentSnapshot.toObject(Book.class);
 *   book.getId()        → 문서 ID (예: "총_균_쇠")
 *   book.isOn("millie") → 밀리의서재에 있으면 true
 *
 * 팀 공통 클래스입니다. 필드 이름이 Firestore와 똑같아야 해서 수정하지 마세요.
 */
public class Book {

    // 플랫폼 키 (books의 platforms, plans 문서 ID와 같음)
    public static final String MILLIE = "millie";
    public static final String RIDI = "ridi";
    public static final String CREMA = "crema";
    public static final String SAM_UNLIMITED = "samUnlimited";
    public static final String SAM_PREMIUM = "samPremium";

    // 화면에 보여줄 순서
    public static final String[] PLATFORM_KEYS = {MILLIE, RIDI, CREMA, SAM_UNLIMITED, SAM_PREMIUM};

    @DocumentId
    private String id;          // 문서 ID (자동으로 채워짐)
    private String title;       // 제목
    private String author;      // 저자
    private String publisher;   // 출판사
    private String category;    // 분야 (소설, 인문 등)
    private String isbn;        // ISBN (비어 있을 수 있음)
    private String thumbnail;   // 표지 이미지 주소 (카카오 책 검색, 비어 있을 수 있음)
    private Map<String, Boolean> platforms = new HashMap<>();

    // Firestore가 객체를 만들 때 필요 (지우지 말 것)
    public Book() {
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public String getCategory() { return category; }
    public String getIsbn() { return isbn; }
    public String getThumbnail() { return thumbnail; }
    public Map<String, Boolean> getPlatforms() { return platforms; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setCategory(String category) { this.category = category; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public void setPlatforms(Map<String, Boolean> platforms) { this.platforms = platforms; }

    /** 해당 플랫폼에서 읽을 수 있으면 true */
    public boolean isOn(String platformKey) {
        if (platforms == null) return false;
        Boolean value = platforms.get(platformKey);
        return value != null && value;
    }

    /** 어느 구독에도 없으면 true */
    public boolean isOnNothing() {
        for (String key : PLATFORM_KEYS) {
            if (isOn(key)) return false;
        }
        return true;
    }

    /** 플랫폼 키 → 화면에 보여줄 이름 */
    public static String platformName(String platformKey) {
        switch (platformKey) {
            case MILLIE: return "밀리의서재";
            case RIDI: return "리디셀렉트";
            case CREMA: return "크레마클럽";
            case SAM_UNLIMITED: return "sam 무제한";
            case SAM_PREMIUM: return "sam 프리미엄";
            default: return platformKey;
        }
    }
}
