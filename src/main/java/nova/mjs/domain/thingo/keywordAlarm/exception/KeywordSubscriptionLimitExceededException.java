package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 소유자(회원 또는 비로그인 기기)당 키워드 알림 최대 개수를 넘겨 등록하려 할 때 발생.
 */
public class KeywordSubscriptionLimitExceededException extends KeywordAlarmException {

    public KeywordSubscriptionLimitExceededException() {
        super(ErrorCode.KEYWORD_SUBSCRIPTION_LIMIT_EXCEEDED);
    }
}
