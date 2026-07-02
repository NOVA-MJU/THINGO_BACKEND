package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 목록 카드 1개 (건물 목록 / 칩 클릭 장소 목록 공용).
 *
 * 건물과 장소를 같은 카드 형태로 보여주되, 종류별로 일부 필드만 채운다.
 * - 건물: classroomCode 채움, location 비움
 * - 장소: location 채움(건물명+층수 또는 도로명주소), classroomCode 비움
 * imageUrl 유무에 따라 프론트 레이아웃이 달라진다.
 */
@Getter
@Builder
public class PinSummaryResponse {

    /** 핀 ID (상세 페이지 요청에 사용) */
    private final Long id;
    /** 종류 (BUILDING / PLACE) - 프론트가 상세 화면을 분기 */
    private final String type;
    /** 건물명/장소명 */
    private final String name;
    /** 소속 카테고리 코드 */
    private final String categoryCode;
    /** 카드 아이콘 키 (카테고리 아이콘) */
    private final String iconKey;
    /** 이미지 URL. 없으면 null (레이아웃 분기) */
    private final String imageUrl;
    /** 예시 강의실 코드 (건물만). 없으면 null */
    private final String classroomCode;
    /** 위치 텍스트 (장소만: 건물명+층수 또는 주소). 없으면 null */
    private final String location;
    /** 즐겨찾기 여부 (비로그인 시 false) */
    private final boolean favorite;
    /** 운영 상태 한글 라벨 (예: "운영중"). 운영시간 없으면 null */
    private final String operatingStatus;
    /** 현재 위치로부터의 거리(미터). 캠퍼스 밖이거나 GPS 없으면 null(미표시) */
    private final Integer distanceMeters;
    /** 지도 마커용 위도. 내부 장소는 소속 건물 좌표로 대체. 좌표 없으면 null */
    private final Double latitude;
    /** 지도 마커용 경도. 내부 장소는 소속 건물 좌표로 대체. 좌표 없으면 null */
    private final Double longitude;

    public static PinSummaryResponse of(Long id, String type, String name, String categoryCode,
                                        String iconKey, String imageUrl, String classroomCode,
                                        String location, boolean favorite, String operatingStatus,
                                        Integer distanceMeters, Double latitude, Double longitude) {
        return PinSummaryResponse.builder()
                .id(id)
                .type(type)
                .name(name)
                .categoryCode(categoryCode)
                .iconKey(iconKey)
                .imageUrl(imageUrl)
                .classroomCode(classroomCode)
                .location(location)
                .favorite(favorite)
                .operatingStatus(operatingStatus)
                .distanceMeters(distanceMeters)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}
