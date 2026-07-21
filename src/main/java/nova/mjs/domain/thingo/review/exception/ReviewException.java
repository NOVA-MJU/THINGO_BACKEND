package nova.mjs.domain.thingo.review.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 리뷰 도메인 공통 추상 예외. 구체 예외는 이 클래스를 상속한다.
 * (CLAUDE.md: 도메인당 추상 예외 1개 + 구체 최소화, 상세 분류는 ErrorCode)
 */
public abstract class ReviewException extends BusinessBaseException {

    protected ReviewException(ErrorCode errorCode) {
        super(errorCode);
    }

    protected ReviewException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
