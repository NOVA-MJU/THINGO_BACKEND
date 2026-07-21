package nova.mjs.domain.thingo.review.service.command;

import nova.mjs.domain.thingo.review.dto.ReviewDTO;

import java.util.UUID;

/**
 * 리뷰 생성/삭제 커맨드 서비스.
 */
public interface ReviewCommandService {

    /** 리뷰 작성. 장소/키워드/미디어 검증 후 저장하고 상세를 반환한다. */
    ReviewDTO.Response.Detail createReview(String email, ReviewDTO.Request.Create request);

    /** 리뷰 삭제. 작성자 또는 OPERATOR만 가능. 미디어 S3 정리 후 하드 삭제. */
    void deleteReview(String email, UUID reviewUuid);
}
