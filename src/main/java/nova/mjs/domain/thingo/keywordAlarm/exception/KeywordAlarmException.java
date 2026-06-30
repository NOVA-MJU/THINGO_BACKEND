package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 키워드 알림 도메인 예외의 공통 상위 타입.
 */
public abstract class KeywordAlarmException extends BusinessBaseException {

    protected KeywordAlarmException(ErrorCode errorCode) {
        super(errorCode);
    }

    protected KeywordAlarmException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
