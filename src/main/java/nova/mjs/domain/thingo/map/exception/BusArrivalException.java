package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 버스 도착 정보 도메인의 최상위 예외 클래스
 * - 모든 버스 관련 예외는 이 클래스를 상속받거나 직접 사용
 */
public class BusArrivalException extends BusinessBaseException {

    public BusArrivalException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusArrivalException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
