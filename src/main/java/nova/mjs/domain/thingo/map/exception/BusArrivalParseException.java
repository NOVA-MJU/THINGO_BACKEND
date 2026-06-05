package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 버스 도착 정보 API 응답 JSON 파싱 실패 시 발생하는 예외
 * - API 응답 구조가 예상과 다르거나 필드가 누락된 경우 발생
 */
public class BusArrivalParseException extends BusArrivalException {

    public BusArrivalParseException() {
        super(ErrorCode.BUS_ARRIVAL_PARSE_FAILED);
    }
}
