package nova.mjs.domain.thingo.news.entity;

import jakarta.persistence.*;
import lombok.*;
import nova.mjs.domain.thingo.ElasticSearch.EntityListner.NewsEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(NewsEntityListener.class)
@Table(name = "news")
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Long id;

    @Column(name = "news_index", nullable = false, unique = true)
    private Long newsIndex; //기사 인덱스

    @Column(nullable = false)
    private String title; //기사 제목

    @Column(nullable = false)
    private LocalDateTime date; // 기사 날짜

    @Column(nullable = false)
    private String reporter; //기자 이름

    @Column(nullable = false)
    private String imageUrl; //이미지

    /**
     * 명대신문 원본은 og:image 를 http(또는 protocol-relative)로 내려준다.
     * 동일 경로가 https 로도 정상 서빙되므로(인증서 정상), 응답/색인에는 https 로 통일해 노출한다.
     * DB 값이 http 로 적재돼 있어도 이 getter 가 https 로 보정하므로 별도 마이그레이션 없이 일관성이 유지된다.
     * (Lombok @Getter 는 수동 getter 가 있으면 생성하지 않는다)
     */
    public String getImageUrl() {
        return toHttps(imageUrl);
    }

    /** http:// 또는 // 로 시작하는 URL 을 https:// 로 정규화. 이미 https 거나 빈 값이면 그대로 둔다. */
    public static String toHttps(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    @Column(columnDefinition = "TEXT")
    private String summary; //기사 첫 문단

    @Column(nullable = false, unique = true)
    private String link; //기사 링크

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Category category; //기사 카테고리

    public enum Category {
        REPORT, SOCIETY;

        public static Category fromStringTOUppercase(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Category cannot be null or blank");
            }
            try {
                return Category.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("Invalid category value: " + value);
            }
        }
    }
}

