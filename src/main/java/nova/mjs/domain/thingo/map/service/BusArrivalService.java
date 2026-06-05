package nova.mjs.domain.thingo.map.service;

import nova.mjs.domain.thingo.map.dto.BusArrivalResponse;

/**
 * 버스 도착 정보 서비스 인터페이스
 *
 * [역할]
 * - 컨트롤러와 구현체 사이의 계약을 정의
 * - 구현체 교체 시 컨트롤러 변경 없이 대응 가능 (의존성 역전 원칙)
 */
public interface BusArrivalService {

    /**
     * 정류장 키(A/B)의 실시간 버스 도착 정보 조회
     *
     * - 프론트는 정류장 키만 전달하고 실제 arsId는 백엔드가 보유/해석한다
     *
     * @param stationKey 정류장 선택 키 ("A" 또는 "B")
     * @param email      현재 로그인 회원 이메일 (비로그인 시 null) - 즐겨찾기 마킹/정렬에 사용
     * @return 정류장 정보 및 버스 도착 목록 (즐겨찾기 노선이 상단에 정렬됨)
     * @throws nova.mjs.domain.thingo.map.exception.BusArrivalException 정류장 키가 잘못됐거나(BAD_REQUEST) 도착 정보 없을 시(NOT_FOUND)
     * @throws nova.mjs.domain.thingo.map.exception.BusArrivalApiCallException API 호출 실패 시
     * @throws nova.mjs.domain.thingo.map.exception.BusArrivalParseException 응답 파싱 실패 시
     */
    BusArrivalResponse getArrivalsByStation(String stationKey, String email);
}
