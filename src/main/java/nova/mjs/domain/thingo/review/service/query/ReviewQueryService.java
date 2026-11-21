package nova.mjs.domain.thingo.review.service.query;

import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import java.util.List;
import java.util.UUID;

/**
 * 리뷰 조회 서비스. email이 null이면 비로그인 조회(차단 필터·isLiked·isMine 미적용).
 */
public interface ReviewQueryService {

    /** 장소별 리뷰 무한 스크롤 목록(최신순/좋아요순, 차단·자가신고 제외) */
    ReviewDTO.Response.CursorPage getReviews(
            Long pinId, String sort, String cursor, int size, String email);

    /** 리뷰 단건 상세. 없거나 차단 관계면 REVIEW_NOT_FOUND */
    ReviewDTO.Response.Detail getReview(UUID reviewUuid, String email);

    /** 장소 상세 상단 사진·영상 스트립(최신 리뷰부터 limit개) */
    List<ReviewDTO.Response.MediaStripItem> getMediaStrip(Long pinId, int limit, String email);

    /** 작성 화면용 키워드 카탈로그(pinId가 F&B면 전용 키워드 포함, null이면 전체) */
    ReviewDTO.Response.KeywordCatalog getKeywordCatalog(Long pinId);

    /** 내가 쓴 리뷰 전체(숨김 제외, 최신순)와 그 장소 id·이름·좌표 */
    List<ReviewDTO.Response.MyReview> getMyReviews(String email);
}
