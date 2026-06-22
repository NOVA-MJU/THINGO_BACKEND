package nova.mjs.domain.thingo.map.support;

import org.springframework.stereotype.Component;

/**
 * 명지대(서울 인문캠퍼스) 지리적 영역 기준값.
 *
 * 거리 표시/정렬의 기준점을 정하는 데 쓰인다.
 * - 사용자가 캠퍼스 반경 안에 있으면: 사용자 현재 위치를 기준으로 거리 계산·표시
 * - 캠퍼스 밖이면: 거리는 표시하지 않고(미표시), 정렬만 정문 기준으로 한다
 *
 * 좌표는 근사값이며 추후 정확한 측정값으로 교정한다.
 */
@Component
public class CampusArea {

    /** 캠퍼스 중심 위도 (반경 판정 기준) */
    public static final double CENTER_LATITUDE = 37.5802;
    /** 캠퍼스 중심 경도 */
    public static final double CENTER_LONGITUDE = 126.9226;

    /** 정문 위도 (캠퍼스 밖 사용자의 정렬 기준점) */
    public static final double MAIN_GATE_LATITUDE = 37.5797;
    /** 정문 경도 */
    public static final double MAIN_GATE_LONGITUDE = 126.9232;

    /** 서비스 이용 가능 반경 (미터) */
    public static final double SERVICE_RADIUS_METERS = 500.0;
}
