package nova.mjs.domain.thingo.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * PostgreSQL 통합 검색 응답 DTO.
 *
 * ES 패키지의 동일 DTO 와 의도적으로 분리한다.
 *
 * 정책
 * - 도메인별 차이는 type 으로 분기.
 * - highlightedTitle / highlightedContent 는 표시용 최종 텍스트.
 *   하이라이트 없으면 원문 fallback (프론트 null 회피).
 * - date 는 Instant 통일.
 * - 확장 필드는 nullable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponseDTO {

    private String id;

    private String highlightedTitle;
    private String highlightedContent;

    private Instant date;

    private String link;
    private String category;
    private String type;
    private String imageUrl;

    /** 관련도 점수 (PG: ts_rank + similarity + popularity + hotBoost) */
    private float score;

    private String authorName;
    private Integer likeCount;
    private Integer commentCount;
}
