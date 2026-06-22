package nova.mjs.domain.thingo.map.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지도 위 핀(Pin)의 종류.
 *
 * 명지도에서 지도에 찍히는 모든 점은 "건물"이거나 "장소"다.
 * - BUILDING: 강의실 코드·층별 안내·운영시간을 가진 캠퍼스 건물 (종합관, 도서관 등)
 * - PLACE   : 건물 안 시설(프린터, 라운지)이거나 건물 밖 가게(맛집, 편의점)
 *
 * 상세 페이지 구성이 둘이 다르기 때문에(건물엔 강의실코드, 장소엔 주소) 타입으로 구분한다.
 */
@Getter
@RequiredArgsConstructor
public enum PinType {

    BUILDING("건물"),
    PLACE("장소");

    private final String description;
}
