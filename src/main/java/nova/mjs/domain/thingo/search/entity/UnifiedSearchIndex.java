package nova.mjs.domain.thingo.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PostgreSQL 통합 검색 인덱스 엔티티.
 *
 * - id 규칙: {TYPE}:{ORIGINAL_ID}
 * - search_vector 컬럼은 DB 트리거가 관리한다 (insert/update 금지).
 * - search_tokens 는 애플리케이션에서 Komoran 토큰화 결과를 저장한다.
 */
@Entity
@Table(name = "unified_search_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnifiedSearchIndex {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "original_id", length = 64, nullable = false)
    private String originalId;

    @Column(name = "type", length = 32, nullable = false)
    private String type;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_name", length = 64)
    private String authorName;

    @Column(name = "link", columnDefinition = "TEXT")
    private String link;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "popularity")
    private Double popularity;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    @Column(name = "search_tokens", columnDefinition = "TEXT")
    private String searchTokens;

    /**
     * DB 트리거가 자동 갱신.
     * - JPA에서는 insert/update 대상 제외.
     */
    @Column(name = "search_vector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String searchVector;

    @Builder(access = AccessLevel.PRIVATE)
    private UnifiedSearchIndex(String id,
                               String originalId,
                               String type,
                               String category,
                               String title,
                               String content,
                               String authorName,
                               String link,
                               String imageUrl,
                               Integer likeCount,
                               Integer commentCount,
                               Double popularity,
                               Boolean active,
                               Instant date,
                               Instant indexedAt,
                               String searchTokens) {
        this.id = id;
        this.originalId = originalId;
        this.type = type;
        this.category = category;
        this.title = title;
        this.content = content;
        this.authorName = authorName;
        this.link = link;
        this.imageUrl = imageUrl;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.popularity = popularity;
        this.active = active;
        this.date = date;
        this.indexedAt = indexedAt;
        this.searchTokens = searchTokens;
    }

    public static UnifiedSearchIndex of(String id,
                                        String originalId,
                                        String type,
                                        String category,
                                        String title,
                                        String content,
                                        String authorName,
                                        String link,
                                        String imageUrl,
                                        Integer likeCount,
                                        Integer commentCount,
                                        Double popularity,
                                        Instant date,
                                        String searchTokens) {
        return UnifiedSearchIndex.builder()
                .id(id)
                .originalId(originalId)
                .type(type)
                .category(category)
                .title(title)
                .content(content)
                .authorName(authorName)
                .link(link)
                .imageUrl(imageUrl)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .popularity(popularity == null ? 0.0d : popularity)
                .active(true)
                .date(date)
                .indexedAt(Instant.now())
                .searchTokens(searchTokens)
                .build();
    }

    public void updateFrom(UnifiedSearchIndex source) {
        this.originalId = source.originalId;
        this.type = source.type;
        this.category = source.category;
        this.title = source.title;
        this.content = source.content;
        this.authorName = source.authorName;
        this.link = source.link;
        this.imageUrl = source.imageUrl;
        this.likeCount = source.likeCount;
        this.commentCount = source.commentCount;
        this.popularity = source.popularity;
        this.active = source.active;
        this.date = source.date;
        this.indexedAt = Instant.now();
        this.searchTokens = source.searchTokens;
    }

    public void deactivate() {
        this.active = false;
        this.indexedAt = Instant.now();
    }
}
