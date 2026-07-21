package nova.mjs.domain.thingo.review.service.query;

import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * 리뷰 조회 서비스. email이 null이면 비로그인 조회(차단 필터·isLiked·isMine 미적용).
 */
public interface ReviewQueryService {

    /** 장소별 리뷰 목록(최신순, 차단 사용자 제외) */
    Page<ReviewDTO.Response.Summary> getReviews(Long pinId, Pageable pageable, String email);

    /** 리뷰 단건 상세. 없거나 차단 관계면 REVIEW_NOT_FOUND */
    ReviewDTO.Response.Detail getReview(UUID reviewUuid, String email);

    /** 장소 상세 상단 사진·영상 스트립(최신 리뷰부터 limit개) */
    List<ReviewDTO.Response.MediaStripItem> getMediaStrip(Long pinId, int limit, String email);

    /** 작성 화면용 키워드 카탈로그(pinId가 F&B면 전용 키워드 포함, null이면 전체) */
    ReviewDTO.Response.KeywordCatalog getKeywordCatalog(Long pinId);
}
