package nova.mjs.domain.thingo.map.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 칩(카테고리)을 눌렀을 때 무엇을 보여줄지 결정하는 종류.
 *
 * 칩마다 클릭 결과가 다르기 때문에 프론트가 분기할 수 있도록 타입을 내려준다.
 * - PLACE_LIST   : 장소 목록을 보여준다 (예: 프린터, 라운지, 대동명지도 맛집)
 * - BUILDING_LIST: 건물 목록을 보여준다 (예: '건물' 칩)
 * - BUS          : 목록이 아니라 버스 전용 화면으로 이동한다 (기존 /bus API 사용)
 */
@Getter
@RequiredArgsConstructor
public enum CategoryResultType {

    PLACE_LIST("장소 목록"),
    BUILDING_LIST("건물 목록"),
    BUS("버스 화면 이동");

    private final String description;
}
