package nova.mjs.domain.thingo.search.dto;

import java.time.Instant;

/**
 * 통합 검색 native query 결과 행.
 *
 * - 컬럼 순서/이름은 UnifiedSearchIndexQueryRepositoryImpl 와 결합.
 */
public record SearchResultRow(
        String id,
        String originalId,
        String type,
        String category,
        String title,
        String highlightedTitle,
        String content,
        String highlightedContent,
        String authorName,
        String link,
        String imageUrl,
        Integer likeCount,
        Integer commentCount,
        Instant date,
        Double score
) {
}
