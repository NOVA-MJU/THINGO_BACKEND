package nova.mjs.domain.thingo.review.exception;

import nova.mjs.util.exception.ErrorCode;

/** 리뷰 삭제 권한 없음(작성자·OPERATOR 아님). */
public class ReviewForbiddenException extends ReviewException {

    public ReviewForbiddenException() {
        super(ErrorCode.REVIEW_FORBIDDEN);
    }
}
