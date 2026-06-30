package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 회원이 이미 등록한 키워드를 다시 등록하려 할 때 발생.
 */
public class DuplicateKeywordException extends KeywordAlarmException {

    public DuplicateKeywordException() {
        super(ErrorCode.DUPLICATE_KEYWORD);
    }
}
