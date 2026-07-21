package nova.mjs.domain.thingo.review.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 리뷰 작성 입력값 검증 실패. 구체 사유는 ErrorCode로 구분한다
 * (카테고리 부적합/키워드 개수/키워드 조합/키워드 미허용/미디어 개수 등).
 */
public class ReviewValidationException extends ReviewException {

    public ReviewValidationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
