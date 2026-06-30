package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 요청한 키워드 구독이 없거나 요청자의 소유가 아닐 때 발생.
 * (타인 구독의 존재 여부를 노출하지 않도록 NOT_FOUND 로 통일)
 */
public class KeywordSubscriptionNotFoundException extends KeywordAlarmException {

    public KeywordSubscriptionNotFoundException() {
        super(ErrorCode.KEYWORD_SUBSCRIPTION_NOT_FOUND);
    }
}
