package nova.mjs.domain.thingo.map.dto;

import nova.mjs.domain.thingo.map.entity.Pin;

/**
 * 네이버 지도 기본 글자를 눌렀을 때 찾은 우리 핀. 앱은 type에 따라 건물 상세 또는 장소 상세를 연다.
 */
public record SymbolMatchResponse(Long id, String type, String name, Double latitude, Double longitude) {

    public static SymbolMatchResponse from(Pin pin) {
        return new SymbolMatchResponse(
                pin.getId(), pin.getType().name(), pin.getName(), pin.getLatitude(), pin.getLongitude());
    }
}
