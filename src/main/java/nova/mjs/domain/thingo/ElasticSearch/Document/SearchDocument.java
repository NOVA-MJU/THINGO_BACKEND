package nova.mjs.domain.thingo.ElasticSearch.Document;

import java.time.Instant;
import java.util.List;

/**
 * SearchDocument
 *
 * - 도메인별 Elasticsearch Document의 공통 인터페이스
 * - Elasticsearch 저장/검색 기준 타입만 노출한다
 * - 표현(LocalDateTime) 타입은 절대 포함하지 않는다
 */
public interface SearchDocument {

    String getId();

    String getTitle();

    String getContent();

    String getType();

    /**
     * Elasticsearch 저장용 절대 시점
     * - 반드시 Instant
     */
    Instant getInstant();

    /**
     * 유효 마감 시점(있으면). null = 무기한.
     * - 학사일정/학과일정 등 명시적 종료일이 있는 도메인만 override 한다.
     * - 공지의 본문 추정 마감은 매퍼(DeadlineExtractor)에서 채운다.
     */
    default Instant getValidUntil() {
        return null;
    }

    default String getCategory() {
        return null;
    }

    default String getLink() {
        return null;
    }

    default String getImageUrl() {
        return null;
    }

    default Integer getLikeCount() {
        return null;
    }

    default Integer getCommentCount() {
        return null;
    }

    default String getAuthorName() {
        return null;
    }
}
