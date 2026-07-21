package nova.mjs.domain.thingo.review.exception;

import nova.mjs.util.exception.ErrorCode;

/** 리뷰 단건 조회 실패(존재하지 않거나 차단 관계로 숨김). */
public class ReviewNotFoundException extends ReviewException {

    public ReviewNotFoundException() {
        super(ErrorCode.REVIEW_NOT_FOUND);
    }
}
