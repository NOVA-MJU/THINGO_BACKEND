package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.OperatingHour;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.repository.FavoritePlaceRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.repository.CategoryRepository;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
import nova.mjs.domain.thingo.map.support.MapSearchRouteResolver;
import nova.mjs.domain.thingo.map.support.OperatingStatusResolver;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 명지도 특화 검색 서비스.
 *
 * 기존 통합검색(unified_search_index)과 분리된, 명지도(건물/장소) 전용 검색이다.
 * 캠퍼스 규모(핀 수백 개)라 DB 전문검색 대신 전체 핀을 메모리에 올려 스코어링한다
 * (거리 계산을 인메모리 Haversine으로 하는 것과 동일한 판단).
 *
 * [검색 규칙]
 * - 분기: 실내 코드 정확일치 → 카테고리 라벨 정확일치 → 일반 이름 검색
 * - 응답: 각 항목의 type이 BUILDING/PLACE/FLOOR_MAP 중 하나이며 한 목록에 섞일 수 있다
 * - 일반 매칭/관련도: {@link MapSearchMatcher} (정규화·초성·오타허용)
 * - 정렬: 관련도 우선 → 동점 시 가까운 순 → 이름순
 * - 거리: 사용자가 캠퍼스 안이면 표시, 밖이면 미표시(정렬만 정문 기준), GPS 없으면 null
 * - 운영상태: 건물은 자기 운영시간, 내부 장소는 소속 건물 운영시간 상속, 외부 장소는 미표시
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapSearchService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 통칭 검색어 → 묶어서 보여줄 카테고리 코드.
     *
     * '음식점' 카테고리에는 교내 식당(애슐리퀸즈) 하나뿐이고, 실제 식당 대부분은 대동명지도 하위 탭
     * (한식/일식/중식/분식/고기/양식)에 흩어져 있다. 라벨 정확 일치만 보면 '음식점' 검색 결과가 1곳뿐이라,
     * 통칭으로 검색하면 식사할 수 있는 곳 전체를 보여준다. 주류·카페는 식사 장소가 아니라 뺀다.
     */
    private static final Map<String, List<String>> CATEGORY_ALIASES = Map.of(
            "음식점", List.of(
                    "restaurant", "cafeteria", "truck",
                    "daedong-kr", "daedong-jp", "daedong-cn",
                    "daedong-snack", "daedong-meat", "daedong-western"));

    /** 통칭으로 걸린 핀의 자동완성 점수. 매처의 카테고리명 보조 가중과 같은 급(이름 매칭보다 아래). */
    private static final double CATEGORY_ALIAS_SCORE = 25.0;

    private final PinRepository pinRepository;
    private final CategoryRepository categoryRepository;
    private final FavoritePlaceRepository favoritePlaceRepository;
    private final MemberRepository memberRepository;
    private final DistanceCalculator distanceCalculator;
    private final OperatingStatusResolver operatingStatusResolver;
    private final MapSearchMatcher matcher;
    private final MapPinService mapPinService;

    /** 관련도 정렬 계산용 임시 행 (핀 + 관련도 점수 + 정렬 거리) */
    private record Scored(Pin pin, double relevance, Double sortDistance) {}

    /**
     * 명지도 검색. 기존 목록 응답을 유지하고 각 항목의 type과 link로 이동 방식을 표현한다.
     *
     * @param keyword    검색어 (비면 빈 목록)
     * @param userLat    사용자 현재 위도 (거리 계산/정렬용, 없으면 null)
     * @param userLng    사용자 현재 경도
     * @param page       0부터 시작하는 페이지 번호
     * @param size       페이지 크기
     * @param email      로그인 회원 이메일 (즐겨찾기 표시용, 비로그인 null)
     * @param recommendationSeed 대동명지도 추천 순서를 고정할 선택 seed
     */
    public List<PinSummaryResponse> search(String keyword, Double userLat, Double userLng, int page, int size,
                                           String email, String recommendationSeed) {
        return search(keyword, userLat, userLng, page, size, email, recommendationSeed, false);
    }

    /**
     * @param floorMap 층별안내도 모드. true면 등록 라벨(카테고리) 검색 결과를 내부 시설만으로 제한한다
     *                 (외부 장소·대동명지도 제외). 일반 이름 검색은 캠퍼스 전역이라 필터하지 않는다.
     */
    public List<PinSummaryResponse> search(String keyword, Double userLat, Double userLng, int page, int size,
                                           String email, String recommendationSeed, boolean floorMap) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        Pin exactIndoorPin = findExactIndoorPin(keyword);
        if (exactIndoorPin != null) {
            if (page > 0) {
                return List.of();
            }
            Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
            boolean insideCampus = distanceCalculator.isWithinCampus(userLat, userLng);
            return List.of(toSummary(exactIndoorPin, favoriteIds, userLat, userLng,
                    insideCampus, LocalDateTime.now(KST)));
        }

        // 통칭('음식점')은 여러 카테고리를 합친 목록. 같은 이름의 단일 라벨보다 먼저 본다.
        List<String> aliasCategoryCodes = aliasCategoryCodes(keyword);
        if (!aliasCategoryCodes.isEmpty()) {
            return mapPinService.getPinsByCategoryCodes(
                    aliasCategoryCodes, userLat, userLng, page, size, email, floorMap);
        }

        Category exactCategory = findExactCategory(keyword);
        if (exactCategory != null) {
            return mapPinService.getPinsByCategory(
                    exactCategory.getCode(), userLat, userLng, page, size, email, recommendationSeed, floorMap);
        }

        return searchGeneral(keyword, userLat, userLng, page, size, email);
    }

    private List<PinSummaryResponse> searchGeneral(String keyword, Double userLat, Double userLng, int page, int size,
                                                   String email) {

        // 검색 대상 핀을 연관과 함께 한 번에 로딩 (카테고리/소속건물/층)
        List<Pin> candidates = pinRepository.findAllForSearch();
        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
        LocalDateTime now = LocalDateTime.now(KST);

        boolean hasGps = userLat != null && userLng != null;
        boolean insideCampus = distanceCalculator.isWithinCampus(userLat, userLng);

        // 1. 관련도 점수 계산 (0 이하는 매칭 실패로 제외)
        List<Scored> scored = new ArrayList<>();
        for (Pin pin : candidates) {
            double relevance = matcher.score(
                    pin.getName(), null, pin.getIndoorCode(), keyword);
            if (relevance <= 0.0) {
                continue;
            }
            scored.add(new Scored(pin, relevance, sortDistance(pin, userLat, userLng, hasGps, insideCampus)));
        }

        // 2. 관련도 우선 → 가까운 순 → 이름순 정렬
        scored.sort(Comparator
                .comparingDouble(Scored::relevance).reversed()
                .thenComparing(Scored::sortDistance, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.pin().getName()));

        // 3. 페이지로 잘라 카드 DTO로 변환
        return paginate(scored, page, size).stream()
                .map(s -> toSummary(s.pin(), favoriteIds, userLat, userLng, insideCampus, now))
                .toList();
    }

    /**
     * 검색 자동완성. 이름·카테고리명 매칭 상위 항목의 이름/아이콘만 경량 반환한다.
     * 거리·운영상태는 계산하지 않는다.
     *
     * @param keyword    검색어 (비면 빈 목록)
     * @param limit      최대 개수 (0 이하면 10)
     */
    public List<MapSuggestResponse> suggest(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int safeLimit = limit > 0 ? limit : 10;

        // 호실 코드를 끝까지 입력했으면 유사 호실 없이 그 호실 하나만 제안한다
        Pin exactIndoorPin = findExactIndoorPin(keyword);
        if (exactIndoorPin != null) {
            return List.of(MapSuggestResponse.from(exactIndoorPin));
        }

        Set<String> aliasCategoryCodes = Set.copyOf(aliasCategoryCodes(keyword));
        return pinRepository.findAllForSearch().stream()
                .map(pin -> new Scored(pin, suggestScore(pin, keyword, aliasCategoryCodes), null))
                .filter(s -> s.relevance() > 0.0)
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed()
                        .thenComparing(s -> s.pin().getName()))
                .limit(safeLimit)
                .map(s -> MapSuggestResponse.from(s.pin()))
                .toList();
    }

    // ====================== 내부 헬퍼 ======================

    /** 통칭 검색어면 묶을 카테고리 코드, 아니면 빈 목록 */
    private List<String> aliasCategoryCodes(String keyword) {
        return CATEGORY_ALIASES.getOrDefault(matcher.normalize(keyword), List.of());
    }

    /** 자동완성 점수: 이름·카테고리명 매칭에 더해, 통칭에 속한 카테고리의 핀도 후보로 올린다 */
    private double suggestScore(Pin pin, String keyword, Set<String> aliasCategoryCodes) {
        double score = matcher.score(pin.getName(), pin.getCategory().getLabel(), pin.getIndoorCode(), keyword);
        if (aliasCategoryCodes.contains(pin.getCategory().getCode())) {
            return Math.max(score, CATEGORY_ALIAS_SCORE);
        }
        return score;
    }

    /** 핀 1개를 검색 결과 카드 DTO로 변환 */
    private PinSummaryResponse toSummary(Pin pin, Set<Long> favoriteIds,
                                         Double userLat, Double userLng, boolean insideCampus, LocalDateTime now) {
        boolean favorite = favoriteIds.contains(pin.getId());
        String classroomCode = pin.getType() == PinType.BUILDING ? pin.getClassroomCode() : null;
        String location = pin.getType() == PinType.PLACE ? locationText(pin) : null;

        return PinSummaryResponse.of(
                pin.getId(),
                MapSearchRouteResolver.responseType(pin),
                pin.getName(),
                pin.getCategory().getCode(),
                pin.getCategory().getIconKey(),
                pin.getImageUrl(),
                classroomCode,
                location,
                favorite,
                operatingStatusLabel(pin, now),
                displayDistance(pin, userLat, userLng, insideCampus),
                pin.resolveLatitude(),
                pin.resolveLongitude(),
                pin.getIndoorCode(),
                MapSearchRouteResolver.link(pin));
    }

    /**
     * 실내 코드는 공백·기호·대소문자를 무시하되 정확히 일치할 때만 층 안내도로 보낸다.
     * 호수만 입력해도(1353) S를 붙여 정확 일치로 본다.
     */
    private Pin findExactIndoorPin(String keyword) {
        return pinRepository.findByIndoorCodeOrRoomNumber(matcher.normalize(keyword).toUpperCase())
                .filter(pin -> pin.isInsideBuilding() && pin.getFloor() != null)
                .orElse(null);
    }

    /** 등록된 명지도 라벨과 정확히 일치하면 일반 이름 검색보다 라벨 목록을 우선한다. */
    private Category findExactCategory(String keyword) {
        String normalizedKeyword = matcher.normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return null;
        }
        return categoryRepository.findAll().stream()
                .filter(category -> matcher.normalize(category.getLabel()).equals(normalizedKeyword))
                .sorted(Comparator
                        .comparing(Category::isTopLevel).reversed()
                        .thenComparingInt(Category::getDisplayOrder)
                        .thenComparing(Category::getId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 검색 카드 운영 상태 라벨.
     * - 건물: 자기 운영시간으로 계산
     * - 내부 장소: 소속 건물 운영시간을 상속
     * - 외부 장소/운영시간 미입력: null (미표시)
     */
    private String operatingStatusLabel(Pin pin, LocalDateTime now) {
        List<OperatingHour> hours = null;
        if (pin.getType() == PinType.BUILDING) {
            hours = pin.getOperatingHours();
        } else if (pin.isInsideBuilding()) {
            hours = pin.getParentBuilding().getOperatingHours();
        }
        if (hours == null || hours.isEmpty()) {
            return null;
        }
        return operatingStatusResolver.resolveDisplayLabel(hours, now);
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

    /** 정렬용 거리. 캠퍼스 안=사용자 기준, 캠퍼스 밖=정문 기준, GPS/좌표 없으면 null */
    private Double sortDistance(Pin pin, Double userLat, Double userLng, boolean hasGps, boolean insideCampus) {
        if (!hasGps) {
            return null;
        }
        Double pinLat = pin.resolveLatitude();
        Double pinLng = pin.resolveLongitude();
        if (pinLat == null || pinLng == null) {
            return null;
        }
        if (insideCampus) {
            return distanceCalculator.distanceMeters(userLat, userLng, pinLat, pinLng);
        }
        return distanceCalculator.distanceMeters(
                CampusArea.MAIN_GATE_LATITUDE, CampusArea.MAIN_GATE_LONGITUDE, pinLat, pinLng);
    }

    /** 화면 표시용 거리(미터). 캠퍼스 안일 때만 계산, 그 외 null(미표시) */
    private Integer displayDistance(Pin pin, Double userLat, Double userLng, boolean insideCampus) {
        if (!insideCampus) {
            return null;
        }
        Double pinLat = pin.resolveLatitude();
        Double pinLng = pin.resolveLongitude();
        if (pinLat == null || pinLng == null) {
            return null;
        }
        return (int) Math.round(distanceCalculator.distanceMeters(userLat, userLng, pinLat, pinLng));
    }

    /** 인메모리 페이지네이션 (캠퍼스 규모라 전체 정렬 후 잘라도 충분) */
    private List<Scored> paginate(List<Scored> all, int page, int size) {
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
        return new HashSet<>(favoritePlaceRepository.findDistinctPinIdsByMember(member));
    }
}
