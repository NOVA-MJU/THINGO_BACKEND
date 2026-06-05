package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 공공데이터포털 버스 도착 정보 API 호출 실패 시 발생하는 예외
 * - 네트워크 오류, 타임아웃, 서버 에러(5xx) 등에서 발생
 */
public class BusArrivalApiCallException extends BusArrivalException {

    public BusArrivalApiCallException() {
        super(ErrorCode.BUS_ARRIVAL_API_CALL_FAILED);
    }
}
