package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.Floor;
import nova.mjs.domain.thingo.map.entity.Pin;

import java.util.List;

/**
 * 건물 상세 페이지 응답.
 *
 * 건물의 기본 정보 + 요일별 운영시간 + 카테고리 필터 탭 + 층별 시설 목록을 담는다.
 * 카테고리 탭은 별도 저장값이 아니라 '이 건물에 있는 장소들의 카테고리'에서 유도한 결과다.
 */
@Getter
@Builder
public class BuildingDetailResponse {

    private final Long id;
    private final String name;
    private final String iconKey;
    private final String imageUrl;
    /** 건물 고유 번호 */
    private final Integer buildingNumber;
    /** 예시 강의실 코드 (건물 상세에만 존재) */
    private final String classroomCode;
    private final boolean favorite;
    /** 운영 상태 한글 라벨. 없으면 null */
    private final String operatingStatus;
    /** 현재 위치로부터의 거리(미터). 캠퍼스 밖/GPS 없으면 null */
    private final Integer distanceMeters;
    /** 지도 마커용 위도. 좌표 없으면 null */
    private final Double latitude;
    /** 지도 마커용 경도. 좌표 없으면 null */
    private final Double longitude;
    /** 기타 안내(i) 텍스트 */
    private final String infoText;
    /** 요일별 운영시간 (토글에 나열) */
    private final List<MapOperatingHourLine> weeklyOperatingHours;
    /** 카테고리 필터 탭 (건물 안 장소들의 카테고리에서 유도) */
    private final List<CategoryTab> categoryTabs;
    /** 층별 시설 목록 */
    private final List<FloorPlaces> floors;

    public static BuildingDetailResponse of(Pin building, boolean favorite, String operatingStatus,
                                            Integer distanceMeters, List<MapOperatingHourLine> weeklyOperatingHours,
                                            List<CategoryTab> categoryTabs, List<FloorPlaces> floors) {
        return BuildingDetailResponse.builder()
                .id(building.getId())
                .name(building.getName())
                .iconKey(building.getCategory().getIconKey())
                .imageUrl(building.getImageUrl())
                .buildingNumber(building.getBuildingNumber())
                .classroomCode(building.getClassroomCode())
                .favorite(favorite)
                .operatingStatus(operatingStatus)
                .distanceMeters(distanceMeters)
                .latitude(building.resolveLatitude())
                .longitude(building.resolveLongitude())
                .infoText(building.getInfoText())
                .weeklyOperatingHours(weeklyOperatingHours)
                .categoryTabs(categoryTabs)
                .floors(floors)
                .build();
    }

    /** 카테고리 필터 탭 1개 */
    @Getter
    @Builder
    public static class CategoryTab {
        private final String code;
        private final String label;
        private final String iconKey;

        public static CategoryTab from(Category category) {
            return CategoryTab.builder()
                    .code(category.getCode())
                    .label(category.getLabel())
                    .iconKey(category.getIconKey())
                    .build();
        }
    }

    /** 한 층의 시설 목록 */
    @Getter
    @Builder
    public static class FloorPlaces {
        private final Long floorId;
        /** 층 라벨 (B1, F1). 층 미지정 시설 묶음이면 null */
        private final String floorLabel;
        private final Integer floorOrder;
        /** 층별 안내도 이미지 URL */
        private final String mapImageUrl;
        /** 해당 층의 시설들 */
        private final List<PlaceBrief> places;

        public static FloorPlaces of(Floor floor, List<PlaceBrief> places) {
            return FloorPlaces.builder()
                    .floorId(floor != null ? floor.getId() : null)
                    .floorLabel(floor != null ? floor.getLabel() : null)
                    .floorOrder(floor != null ? floor.getFloorOrder() : null)
                    .mapImageUrl(floor != null ? floor.getMapImageUrl() : null)
                    .places(places)
                    .build();
        }
    }

    /** 층별 목록에 나열되는 시설 1개 */
    @Getter
    @Builder
    public static class PlaceBrief {
        private final Long id;
        private final String name;
        private final String categoryCode;
        private final String iconKey;

        public static PlaceBrief from(Pin place) {
            return PlaceBrief.builder()
                    .id(place.getId())
                    .name(place.getName())
                    .categoryCode(place.getCategory().getCode())
                    .iconKey(place.getCategory().getIconKey())
                    .build();
        }
    }
}
