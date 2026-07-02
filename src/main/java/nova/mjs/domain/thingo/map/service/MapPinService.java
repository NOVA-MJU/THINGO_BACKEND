package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.map.dto.BuildingDetailResponse;
import nova.mjs.domain.thingo.map.dto.MapOperatingHourLine;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.dto.PlaceDetailResponse;
import nova.mjs.domain.thingo.map.entity.*;
import nova.mjs.domain.thingo.map.exception.CategoryNotFoundException;
import nova.mjs.domain.thingo.map.exception.PinNotFoundException;
import nova.mjs.domain.thingo.map.repository.CategoryRepository;
import nova.mjs.domain.thingo.map.repository.PinFavoriteRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.OperatingStatusResolver;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 명지도 건물/장소 조회 서비스.
 *
 * [공통 규칙]
 * - 운영 상태와 거리는 저장값이 아니라 요청 시점에 계산한다.
 * - 거리 정렬/표시 기준점:
 *   · 사용자가 캠퍼스 반경 안 → 사용자 현재 위치 기준으로 계산·표시
 *   · 캠퍼스 밖 → 거리는 표시하지 않고(미표시) 정렬만 정문 기준
 *   · GPS 없음 → 거리 null
 * - 즐겨찾기한 핀은 칩 목록에서 상단으로 정렬한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapPinService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PinRepository pinRepository;
    private final CategoryRepository categoryRepository;
    private final PinFavoriteRepository pinFavoriteRepository;
    private final MemberRepository memberRepository;
    private final DistanceCalculator distanceCalculator;
    private final OperatingStatusResolver operatingStatusResolver;

    /**
     * 건물 전체 목록 (바텀시트). 건물 번호 순으로 고정 노출하며 페이지네이션은 없다.
     * 각 건물의 운영 상태·거리·즐겨찾기 여부를 함께 계산해 내려준다.
     */
    public List<PinSummaryResponse> getBuildings(Double userLat, Double userLng, String email) {
        List<Pin> buildings = pinRepository.findByTypeOrderByBuildingNumberAsc(PinType.BUILDING);

        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
        LocalDateTime now = LocalDateTime.now(KST);

        // 건물은 번호 순서를 유지한다 (즐겨찾기·거리 재정렬 없음)
        return buildings.stream()
                .map(building -> toSummary(building, favoriteIds, userLat, userLng, now))
                .toList();
    }

    /**
     * 칩(카테고리) 클릭 시 보여줄 장소/건물 목록.
     *
     * 하위 탭이 있는 칩(예: 대동명지도)은 자식 카테고리까지 포함해 조회한다.
     * 정렬은 '즐겨찾기 먼저 → 가까운 순'이며, 비건물(장소)은 무한 스크롤을 위해 페이지로 잘라 반환한다.
     *
     * @param categoryCode 칩 코드
     * @param page         0부터 시작하는 페이지 번호
     * @param size         페이지 크기
     */
    public List<PinSummaryResponse> getPinsByCategory(String categoryCode, Double userLat, Double userLng,
                                                      int page, int size, String email) {
        Category category = categoryRepository.findByCode(categoryCode)
                .orElseThrow(CategoryNotFoundException::new);

        // 버스 칩은 목록이 아니라 별도 화면(/bus)으로 이동하므로 여기서는 빈 목록
        if (category.getResultType() == CategoryResultType.BUS) {
            return List.of();
        }

        // 칩 본인 + 하위 탭 코드들을 모아 한 번에 조회
        List<String> categoryCodes = new ArrayList<>();
        categoryCodes.add(category.getCode());
        categoryRepository.findByParentOrderByDisplayOrderAsc(category)
                .forEach(child -> categoryCodes.add(child.getCode()));

        List<Pin> pins = pinRepository.findByCategoryCodeIn(categoryCodes);

        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
        LocalDateTime now = LocalDateTime.now(KST);

        // 즐겨찾기 먼저 → 가까운 순으로 정렬한 뒤 페이지로 자른다
        List<PinSummaryResponse> sorted = toSortedSummaries(pins, favoriteIds, userLat, userLng, now);
        return paginate(sorted, page, size);
    }

    /**
     * 건물 상세. 요일별 운영시간 + 카테고리 필터 탭 + 층별 시설 목록을 구성한다.
     * (카테고리 탭은 이 건물 안 장소들의 카테고리에서 유도한다)
     */
    public BuildingDetailResponse getBuildingDetail(Long buildingId, Double userLat, Double userLng, String email) {
        Pin building = pinRepository.findByIdAndType(buildingId, PinType.BUILDING)
                .orElseThrow(PinNotFoundException::new);

        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
        LocalDateTime now = LocalDateTime.now(KST);

        boolean favorite = favoriteIds.contains(building.getId());
        String operatingStatus = statusLabel(building, now);
        Integer distance = displayDistance(building, userLat, userLng);

        // 요일별 운영시간 (월→일 순)
        List<MapOperatingHourLine> weeklyOperatingHours = building.getOperatingHours().stream()
                .sorted(Comparator.comparing(OperatingHour::getDayOfWeek))
                .map(MapOperatingHourLine::from)
                .toList();

        // 건물 안 장소들로 카테고리 탭과 층별 목록을 구성
        List<Pin> placesInBuilding = pinRepository.findByParentBuildingId(buildingId);
        List<BuildingDetailResponse.CategoryTab> categoryTabs = buildCategoryTabs(placesInBuilding);
        List<BuildingDetailResponse.FloorPlaces> floors = buildFloorPlaces(building, placesInBuilding);

        return BuildingDetailResponse.of(
                building, favorite, operatingStatus, distance, weeklyOperatingHours, categoryTabs, floors);
    }

    /**
     * 장소(비건물) 상세. 위치(건물명+층수 또는 도로명주소)와 요일별 운영시간을 구성한다.
     */
    public PlaceDetailResponse getPlaceDetail(Long placeId, Double userLat, Double userLng, String email) {
        Pin place = pinRepository.findByIdAndType(placeId, PinType.PLACE)
                .orElseThrow(PinNotFoundException::new);

        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));

        boolean favorite = favoriteIds.contains(place.getId());
        Integer distance = displayDistance(place, userLat, userLng);
        String location = locationText(place);

        // 장소(비건물)는 운영시간/상태를 제공하지 않고 위치·추가정보(infoText)만 노출한다
        return PlaceDetailResponse.of(place, favorite, distance, location);
    }

    // ====================== 내부 헬퍼 ======================

    /** 정렬 계산용 임시 행 (핀 + 즐겨찾기 여부 + 정렬 거리 + 표시 거리) */
    private record RankedPin(Pin pin, boolean favorite, Double sortDistance, Integer displayDistance) {}

    /**
     * '즐겨찾기 먼저 → 가까운 순' 정렬된 요약 목록을 만든다.
     */
    private List<PinSummaryResponse> toSortedSummaries(List<Pin> pins, Set<Long> favoriteIds,
                                                       Double userLat, Double userLng, LocalDateTime now) {
        boolean hasGps = userLat != null && userLng != null;
        boolean insideCampus = distanceCalculator.isWithinCampus(userLat, userLng);

        List<RankedPin> ranked = pins.stream()
                .map(pin -> {
                    boolean favorite = favoriteIds.contains(pin.getId());
                    Double pinLat = pin.resolveLatitude();
                    Double pinLng = pin.resolveLongitude();

                    Double sortDistance = null;
                    Integer displayDistance = null;
                    if (hasGps && pinLat != null && pinLng != null) {
                        if (insideCampus) {
                            double meters = distanceCalculator.distanceMeters(userLat, userLng, pinLat, pinLng);
                            sortDistance = meters;
                            displayDistance = (int) Math.round(meters);
                        } else {
                            // 캠퍼스 밖: 거리 미표시, 정렬만 정문 기준
                            sortDistance = distanceCalculator.distanceMeters(
                                    CampusArea.MAIN_GATE_LATITUDE, CampusArea.MAIN_GATE_LONGITUDE, pinLat, pinLng);
                        }
                    }
                    return new RankedPin(pin, favorite, sortDistance, displayDistance);
                })
                .sorted(Comparator
                        .comparing(RankedPin::favorite).reversed()                                   // 즐겨찾기 먼저
                        .thenComparing(RankedPin::sortDistance, Comparator.nullsLast(Comparator.naturalOrder())) // 가까운 순
                        .thenComparing(r -> r.pin().getName()))                                       // 이름순 보조
                .toList();

        return ranked.stream()
                .map(r -> toSummary(r.pin(), favoriteIds, r.displayDistance(), now))
                .toList();
    }

    /** 입력 순서를 유지하며 요약으로 변환 (건물 목록 등 재정렬 불필요한 경우) */
    private PinSummaryResponse toSummary(Pin pin, Set<Long> favoriteIds,
                                         Double userLat, Double userLng, LocalDateTime now) {
        return toSummary(pin, favoriteIds, displayDistance(pin, userLat, userLng), now);
    }

    /** 핀 1개를 목록 카드 DTO로 변환 */
    private PinSummaryResponse toSummary(Pin pin, Set<Long> favoriteIds, Integer displayDistance, LocalDateTime now) {
        boolean favorite = favoriteIds.contains(pin.getId());
        String classroomCode = pin.getType() == PinType.BUILDING ? pin.getClassroomCode() : null;
        String location = pin.getType() == PinType.PLACE ? locationText(pin) : null;

        return PinSummaryResponse.of(
                pin.getId(),
                pin.getType().name(),
                pin.getName(),
                pin.getCategory().getCode(),
                pin.getCategory().getIconKey(),
                pin.getImageUrl(),
                classroomCode,
                location,
                favorite,
                pin.getType() == PinType.BUILDING ? statusLabel(pin, now) : null, // 운영 상태는 건물에만
                displayDistance,
                pin.resolveLatitude(),   // 내부 장소는 소속 건물 좌표로 대체
                pin.resolveLongitude());
    }

    /** 화면에 표시할 거리(미터). 캠퍼스 안일 때만 계산, 그 외 null */
    private Integer displayDistance(Pin pin, Double userLat, Double userLng) {
        if (!distanceCalculator.isWithinCampus(userLat, userLng)) {
            return null;
        }
        Double pinLat = pin.resolveLatitude();
        Double pinLng = pin.resolveLongitude();
        if (pinLat == null || pinLng == null) {
            return null;
        }
        return (int) Math.round(distanceCalculator.distanceMeters(userLat, userLng, pinLat, pinLng));
    }

    /** 운영 상태 한글 라벨 (운영시간 없으면 null) */
    private String statusLabel(Pin pin, LocalDateTime now) {
        OperatingStatus status = operatingStatusResolver.resolve(pin.getOperatingHours(), now);
        return status != null ? status.getLabel() : null;
    }

    /** 장소 위치 텍스트 (내부: 건물명+층수, 외부: 도로명주소) */
    private String locationText(Pin place) {
        if (place.isInsideBuilding()) {
            String buildingName = place.getParentBuilding().getName();
            String floorLabel = place.getFloor() != null ? place.getFloor().getLabel() : null;
            return floorLabel != null ? buildingName + " " + floorLabel : buildingName;
        }
        return place.getAddress();
    }

    /** 건물 안 장소들의 카테고리를 중복 없이 노출 순서대로 모아 필터 탭으로 만든다 */
    private List<BuildingDetailResponse.CategoryTab> buildCategoryTabs(List<Pin> placesInBuilding) {
        Map<String, Category> distinctByCode = new LinkedHashMap<>();
        for (Pin place : placesInBuilding) {
            distinctByCode.putIfAbsent(place.getCategory().getCode(), place.getCategory());
        }
        return distinctByCode.values().stream()
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .map(BuildingDetailResponse.CategoryTab::from)
                .toList();
    }

    /** 건물의 층 순서대로 시설을 묶는다. 층 미지정 시설은 마지막에 별도 묶음으로 둔다 */
    private List<BuildingDetailResponse.FloorPlaces> buildFloorPlaces(Pin building, List<Pin> placesInBuilding) {
        // 층 ID → 그 층의 시설들
        Map<Long, List<Pin>> placesByFloorId = placesInBuilding.stream()
                .filter(place -> place.getFloor() != null)
                .collect(Collectors.groupingBy(place -> place.getFloor().getId()));

        List<BuildingDetailResponse.FloorPlaces> result = building.getFloors().stream()
                .sorted(Comparator.comparingInt(Floor::getFloorOrder))
                .map(floor -> {
                    List<BuildingDetailResponse.PlaceBrief> briefs =
                            placesByFloorId.getOrDefault(floor.getId(), List.of()).stream()
                                    .map(BuildingDetailResponse.PlaceBrief::from)
                                    .toList();
                    return BuildingDetailResponse.FloorPlaces.of(floor, briefs);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // 층이 지정되지 않은 시설 묶음
        List<BuildingDetailResponse.PlaceBrief> noFloor = placesInBuilding.stream()
                .filter(place -> place.getFloor() == null)
                .map(BuildingDetailResponse.PlaceBrief::from)
                .toList();
        if (!noFloor.isEmpty()) {
            result.add(BuildingDetailResponse.FloorPlaces.of(null, noFloor));
        }
        return result;
    }

    /** 인메모리 페이지네이션 (캠퍼스 규모라 전체 정렬 후 잘라도 충분) */
    private List<PinSummaryResponse> paginate(List<PinSummaryResponse> all, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 20;
        int from = safePage * safeSize;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(from + safeSize, all.size());
        return all.subList(from, to);
    }

    /** 로그인 회원 조회 (비로그인이면 null) */
    private Member resolveMember(String email) {
        if (email == null) {
            return null;
        }
        return memberRepository.findByEmail(email).orElse(null);
    }

    /** 회원의 즐겨찾기 핀 ID 집합 (비로그인이면 빈 집합) */
    private Set<Long> favoritePinIds(Member member) {
        if (member == null) {
            return Set.of();
        }
        return new HashSet<>(pinFavoriteRepository.findFavoritePinIds(member));
    }
}
