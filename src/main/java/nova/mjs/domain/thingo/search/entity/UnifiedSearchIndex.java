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
import java.util.Objects;

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

    /**
     * 유효 마감 시점. null = 무기한(학칙 등 만료 개념 없는 문서).
     * - 학사일정/학과일정: 소스 endDate
     * - 공지: 본문에서 추정(DeadlineExtractor), 추정 실패 시 null
     * 검색 랭킹에서 과거 시점이면 후순위로 감점한다(응답 스키마에는 노출하지 않는다).
     */
    @Column(name = "valid_until")
    private Instant validUntil;

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
                               Instant validUntil,
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
        this.validUntil = validUntil;
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
                                        Instant validUntil,
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
                .validUntil(validUntil)
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
        this.validUntil = source.validUntil;
        this.indexedAt = Instant.now();
        this.searchTokens = source.searchTokens;
    }

    public void deactivate() {
        this.active = false;
        this.indexedAt = Instant.now();
    }

    /**
     * 정합성 reconcile 용 변경 감지.
     * 검색 결과/정렬에 영향을 주는 핵심 필드만 비교한다(indexedAt 은 매번 바뀌므로 제외).
     */
    public boolean differsFrom(UnifiedSearchIndex other) {
        if (other == null) {
            return true;
        }
        return !Objects.equals(this.title, other.title)
                || !Objects.equals(this.content, other.content)
                || !Objects.equals(this.category, other.category)
                || !Objects.equals(this.type, other.type)
                || !Objects.equals(this.date, other.date)
                || !Objects.equals(this.validUntil, other.validUntil)
                || !Objects.equals(this.popularity, other.popularity)
                || !Objects.equals(this.searchTokens, other.searchTokens)
                || !Objects.equals(this.active, other.active);
    }
}
