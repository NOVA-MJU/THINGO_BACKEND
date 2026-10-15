package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.Pin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 네이버 장소에서 온 핀 코드(dd-{네이버 place id}) */
    private static final Pattern NAVER_PLACE_CODE = Pattern.compile("^dd-(\\d+)$");

    private final Long id;
    private final String name;
    private final String categoryCode;
    private final String iconKey;
    private final String imageUrl;
    /** 상세 갤러리 사진. 갤러리를 따로 등록하지 않은 장소는 대표 사진(imageUrl) 1장, 사진이 없으면 빈 배열 */
    private final List<String> imageUrls;
    private final boolean favorite;
    /** 현재 위치로부터의 거리(미터). 캠퍼스 밖/GPS 없으면 null */
    private final Integer distanceMeters;
    /** 지도 마커용 위도. 내부 장소는 소속 건물 좌표로 대체. 좌표 없으면 null */
    private final Double latitude;
    /** 지도 마커용 경도. 내부 장소는 소속 건물 좌표로 대체. 좌표 없으면 null */
    private final Double longitude;
    /** 위치 텍스트 (내부: 건물명+층수, 외부: 도로명주소) */
    private final String location;
    /** 추가 정보(i) 텍스트 */
    private final String infoText;
    /**
     * 네이버 지도 장소 페이지 링크. 영업시간·전화·사진은 수집하지 않고 이 링크로 네이버에서 보게 한다.
     * 네이버 장소가 아닌 핀(교내 시설 등)은 null
     */
    private final String naverPlaceUrl;

    public static PlaceDetailResponse of(Pin place, boolean favorite, Integer distanceMeters, String location) {
        return PlaceDetailResponse.builder()
                .id(place.getId())
                .name(place.getName())
                .categoryCode(place.getCategory().getCode())
                .iconKey(place.getCategory().getIconKey())
                .imageUrl(place.getImageUrl())
                .imageUrls(galleryOf(place))
                .favorite(favorite)
                .distanceMeters(distanceMeters)
                .latitude(place.resolveLatitude())
                .longitude(place.resolveLongitude())
                .location(location)
                .infoText(place.getInfoText())
                .naverPlaceUrl(naverPlaceUrlOf(place.getCode()))
                .build();
    }

    static String naverPlaceUrlOf(String code) {
        Matcher matcher = NAVER_PLACE_CODE.matcher(code == null ? "" : code);
        return matcher.matches() ? "https://map.naver.com/p/entry/place/" + matcher.group(1) : null;
    }

    private static List<String> galleryOf(Pin place) {
        if (!place.getImageUrls().isEmpty()) {
            return List.copyOf(place.getImageUrls());
        }
        return place.getImageUrl() == null ? List.of() : List.of(place.getImageUrl());
    }
}
