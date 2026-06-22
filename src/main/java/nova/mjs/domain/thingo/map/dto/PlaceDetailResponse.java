package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.Pin;

/**
 * 장소(비건물) 상세 페이지 응답.
 *
 * 건물 상세와 달리 강의실 코드와 운영시간/운영상태가 없다.
 * 장소는 위치(주소 또는 건물명+층수)와 추가정보(infoText)만 제공한다.
 * (운영시간은 건물에만 부여한다)
 */
@Getter
@Builder
public class PlaceDetailResponse {

    private final Long id;
    private final String name;
    private final String categoryCode;
    private final String iconKey;
    private final String imageUrl;
    private final boolean favorite;
    /** 현재 위치로부터의 거리(미터). 캠퍼스 밖/GPS 없으면 null */
    private final Integer distanceMeters;
    /** 위치 텍스트 (내부: 건물명+층수, 외부: 도로명주소) */
    private final String location;
    /** 추가 정보(i) 텍스트 */
    private final String infoText;

    public static PlaceDetailResponse of(Pin place, boolean favorite, Integer distanceMeters, String location) {
        return PlaceDetailResponse.builder()
                .id(place.getId())
                .name(place.getName())
                .categoryCode(place.getCategory().getCode())
                .iconKey(place.getCategory().getIconKey())
                .imageUrl(place.getImageUrl())
                .favorite(favorite)
                .distanceMeters(distanceMeters)
                .location(location)
                .infoText(place.getInfoText())
                .build();
    }
}
