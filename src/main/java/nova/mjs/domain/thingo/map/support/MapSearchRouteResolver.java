package nova.mjs.domain.thingo.map.support;

import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 지도 검색 항목의 프론트 이동 방식과 층별안내도 링크를 한 곳에서 결정한다.
 *
 * 엔티티의 PinType은 BUILDING/PLACE 도메인 구분을 유지한다. 검색 응답에서만
 * 상위 건물과 층이 모두 있는 PLACE를 FLOOR_MAP으로 표현한다.
 */
public final class MapSearchRouteResolver {

    public static final String FLOOR_MAP_TYPE = "FLOOR_MAP";

    private MapSearchRouteResolver() {
    }

    public static String responseType(Pin pin) {
        return isFloorMapTarget(pin) ? FLOOR_MAP_TYPE : pin.getType().name();
    }

    /**
     * 층별안내도 링크. placeId는 장소 식별(상세/즐겨찾기/리뷰)용이고,
     * target은 프론트가 도면 SVG에서 찾을 텍스트(호실 코드, 없으면 장소명)다.
     * 동기화 키인 Pin.code는 행 순서·카테고리에 따라 바뀔 수 있어 링크에 노출하지 않는다.
     */
    public static String link(Pin pin) {
        if (!isFloorMapTarget(pin)) {
            return null;
        }

        return UriComponentsBuilder.fromPath("/maps/floor")
                .queryParam("buildingId", pin.getParentBuilding().getId())
                .queryParam("floorLabel", pin.getFloor().getLabel())
                .queryParam("placeId", pin.getId())
                .queryParam("target", floorPlanText(pin))
                .build()
                .encode()
                .toUriString();
    }

    private static String floorPlanText(Pin pin) {
        return pin.getIndoorCode() != null ? pin.getIndoorCode() : pin.getName();
    }

    public static boolean isFloorMapTarget(Pin pin) {
        return pin.getType() == PinType.PLACE
                && pin.getParentBuilding() != null
                && pin.getFloor() != null;
    }
}
