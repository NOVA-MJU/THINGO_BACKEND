package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 요청한 알림 내역이 없거나 요청자의 소유가 아닐 때 발생.
 */
public class NotificationHistoryNotFoundException extends KeywordAlarmException {

    public NotificationHistoryNotFoundException() {
        super(ErrorCode.NOTIFICATION_HISTORY_NOT_FOUND);
    }
}
