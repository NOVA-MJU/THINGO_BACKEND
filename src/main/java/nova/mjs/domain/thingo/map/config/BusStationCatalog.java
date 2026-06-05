package nova.mjs.domain.thingo.map.config;

import lombok.Getter;
import lombok.Setter;
import nova.mjs.domain.thingo.map.exception.BusArrivalException;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 명지대 인근 고정 정류장 카탈로그
 *
 * - application.yml의 bus.stations.a / bus.stations.b 설정을 바인딩
 * - 프론트는 정류장 키("A"/"B")만 전달하고, 실제 ARS ID는 백엔드가 보유/해석한다
 * - arsId가 외부로 노출되지 않도록 정류장 선택을 키 기반으로 추상화
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bus.stations")
public class BusStationCatalog {

    /** 정류장 A (예: DMC센트럴아이파크아파트 방면) */
    private Station a;

    /** 정류장 B (예: 명지대삼거리 방면) */
    private Station b;

    /**
     * 정류장 키("A"/"B", 대소문자 무관)를 실제 정류장 정보로 해석한다.
     *
     * @param key 프론트가 전달한 정류장 선택 키
     * @return 해당 정류장 설정 (arsId, name, lat, lng)
     * @throws BusArrivalException 키가 A/B가 아니면 BUS_STATION_NOT_FOUND
     */
    public Station resolve(String key) {
        if (key == null) {
            throw new BusArrivalException(ErrorCode.BUS_STATION_NOT_FOUND);
        }
        return switch (key.trim().toUpperCase()) {
            case "A" -> a;
            case "B" -> b;
            default -> throw new BusArrivalException(ErrorCode.BUS_STATION_NOT_FOUND);
        };
    }

    @Getter
    @Setter
    public static class Station {
        /** 서울 버스 정보 시스템 정류장 고유 ID (내부 보유, 외부 비노출 대상) */
        private String arsId;
        /** 정류장 이름 (방면 포함) */
        private String name;
        /** 위도 */
        private double lat;
        /** 경도 */
        private double lng;
    }
}
