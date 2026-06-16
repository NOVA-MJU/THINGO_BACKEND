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

