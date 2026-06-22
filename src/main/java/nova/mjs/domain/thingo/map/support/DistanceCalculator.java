package nova.mjs.domain.thingo.map.support;

import org.springframework.stereotype.Component;

/**
 * 두 좌표 사이의 직선 거리를 계산하는 유틸 (Haversine 공식).
 *
 * 캠퍼스 규모(핀 수백 개)에서는 DB 공간 확장(PostGIS) 없이 메모리에서 계산·정렬해도 충분하다.
 * 데이터가 크게 늘면 PostGIS 컬럼/인덱스로 무중단 전환할 수 있다.
 */
@Component
public class DistanceCalculator {

    /** 지구 평균 반지름 (미터) */
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * 두 위경도 사이의 직선 거리(미터)를 반환한다.
     */
    public double distanceMeters(double fromLat, double fromLng, double toLat, double toLng) {
        double latDiff = Math.toRadians(toLat - fromLat);
        double lngDiff = Math.toRadians(toLng - fromLng);

        double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(lngDiff / 2) * Math.sin(lngDiff / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * 좌표가 캠퍼스 서비스 반경 안에 있는지 판정한다.
     * 좌표가 없으면(GPS 미수신) 캠퍼스 밖으로 간주한다.
     */
    public boolean isWithinCampus(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        double distance = distanceMeters(
                latitude, longitude,
                CampusArea.CENTER_LATITUDE, CampusArea.CENTER_LONGITUDE);
        return distance <= CampusArea.SERVICE_RADIUS_METERS;
    }
}
