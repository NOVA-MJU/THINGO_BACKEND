package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.Pin;

/**
 * 검색 자동완성 항목 1개.
 *
 * 검색 결과 카드(PinSummaryResponse)와 달리 거리·운영상태를 계산하지 않는 경량 응답이다.
 * 아이콘·종류·ID를 함께 내려 프론트가 드롭다운에 아이콘을 표시하고, 탭 시 바로 상세로 이동할 수 있게 한다.
 */
@Getter
@Builder
public class MapSuggestResponse {

    /** 핀 ID (탭 시 상세 요청에 사용) */
    private final Long id;
    /** 건물명/장소명 (자동완성에 표시) */
    private final String name;
    /** 종류 (BUILDING / PLACE) - 프론트가 상세 화면을 분기 */
    private final String type;
    /** 소속 카테고리 코드 */
    private final String categoryCode;
    /** 카드 아이콘 키 (카테고리 아이콘) */
    private final String iconKey;

    public static MapSuggestResponse from(Pin pin) {
        return MapSuggestResponse.builder()
                .id(pin.getId())
                .name(pin.getName())
                .type(pin.getType().name())
                .categoryCode(pin.getCategory().getCode())
                .iconKey(pin.getCategory().getIconKey())
                .build();
    }
}
