package nova.mjs.domain.thingo.keywordAlarm.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 비로그인 요청인데 X-Device-Id 가 없거나, 서버가 발급하지 않은 값일 때 발생.
 * 앱은 이 응답을 받으면 POST /api/v1/devices 로 기기 id 를 새로 발급받는다.
 */
public class InvalidDeviceIdException extends KeywordAlarmException {

    public InvalidDeviceIdException() {
        super(ErrorCode.DEVICE_ID_INVALID);
    }
}
