package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 구글 시트 → DB 명지도 동기화 요청/결과 DTO.
 *
 * 운영팀 시트의 Apps Script가 탭별 행을 모아 한 번에 POST한다.
 * 각 섹션(groups/categories/buildings/floors/places/operatingHours)은 시트 탭 하나에 대응한다.
 * 백엔드는 code(핀/카테고리/그룹) 또는 (건물+라벨/요일) 키로 upsert 한다.
 */
public class MapSyncDTO {

    /** 동기화 요청 본문 (탭별 행 묶음) */
    @Getter
    @NoArgsConstructor
    public static class SyncRequest {
        private List<GroupRow> groups = new ArrayList<>();
        private List<CategoryRow> categories = new ArrayList<>();
        private List<BuildingRow> buildings = new ArrayList<>();
        private List<FloorRow> floors = new ArrayList<>();
        private List<PlaceRow> places = new ArrayList<>();
        private List<OperatingHourRow> operatingHours = new ArrayList<>();
    }

    /** category_groups 탭 1행 */
    @Getter
    @NoArgsConstructor
    public static class GroupRow {
        private String code;
        private String name;
        private Integer displayOrder;
    }

    /** categories 탭 1행 */
    @Getter
    @NoArgsConstructor
    public static class CategoryRow {
        private String code;
        private String groupCode;
        /** 상위 칩 코드. 최상위 칩이면 비움 */
        private String parentCode;
        private String label;
        private String subtitle;
        private String tooltipText;
        private String iconKey;
        /** PLACE_LIST / BUILDING_LIST / BUS */
        private String resultType;
        private Boolean quickMenu;
        private Integer displayOrder;
    }

    /** buildings 탭 1행 */
    @Getter
    @NoArgsConstructor
    public static class BuildingRow {
        private String code;
        private String categoryCode;
        private String name;
        private Double latitude;
        private Double longitude;
        private String imageUrl;
        private String infoText;
        private Integer buildingNumber;
        private String classroomCode;
    }

    /** floors 탭 1행 (건물 코드 + 층 라벨로 식별) */
    @Getter
    @NoArgsConstructor
    public static class FloorRow {
        private String buildingCode;
        private String label;
        private Integer floorOrder;
        private String mapImageUrl;
    }

    /** places 탭 1행 */
    @Getter
    @NoArgsConstructor
    public static class PlaceRow {
        private String code;
        private String categoryCode;
        private String name;
        private Double latitude;
        private Double longitude;
        private String imageUrl;
        private String infoText;
        private String address;
        /** 내부 장소면 소속 건물 코드, 외부 장소면 비움 */
        private String parentBuildingCode;
        /** 내부 장소면 소속 층 라벨(건물의 floors와 매칭), 외부 장소면 비움 */
        private String floorLabel;
    }

    /** operating_hours 탭 1행 (건물 코드 + 요일로 식별) */
    @Getter
    @NoArgsConstructor
    public static class OperatingHourRow {
        private String buildingCode;
        /** MONDAY ... SUNDAY */
        private String dayOfWeek;
        /** "HH:mm" 형식. 24시간/휴무면 비움 */
        private String openTime;
        private String closeTime;
        private Boolean always24h;
        private Boolean closed;
        private String note;
    }

    /** 동기화 결과 (섹션별 처리 건수) */
    @Getter
    @Builder
    public static class SyncResult {
        private final int groups;
        private final int categories;
        private final int buildings;
        private final int floors;
        private final int places;
        private final int operatingHours;
    }
}
