package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.entity.OperatingHour;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.repository.PinFavoriteRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
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
import java.util.Set;

/**
 * 명지도 특화 검색 서비스.
 *
 * 기존 통합검색(unified_search_index)과 분리된, 명지도(건물/장소) 전용 검색이다.
 * 캠퍼스 규모(핀 수백 개)라 DB 전문검색 대신 전체 핀을 메모리에 올려 스코어링한다
 * (거리 계산을 인메모리 Haversine으로 하는 것과 동일한 판단).
 *
 * [검색 규칙]
 * - 매칭/관련도: {@link MapSearchMatcher} (정규화·초성·오타허용·카테고리 보조)
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

    private final PinRepository pinRepository;
    private final PinFavoriteRepository pinFavoriteRepository;
    private final MemberRepository memberRepository;
    private final DistanceCalculator distanceCalculator;
    private final OperatingStatusResolver operatingStatusResolver;
    private final MapSearchMatcher matcher;

    /** 관련도 정렬 계산용 임시 행 (핀 + 관련도 점수 + 정렬 거리) */
    private record Scored(Pin pin, double relevance, Double sortDistance) {}

    /**
     * 명지도 검색. 이름·카테고리명에 대해 관련도 점수를 매겨 정렬한 뒤 페이지로 잘라 반환한다.
     *
     * @param keyword    검색어 (비면 빈 목록)
     * @param typeFilter 종류 필터 (BUILDING/PLACE, null이면 전체)
     * @param userLat    사용자 현재 위도 (거리 계산/정렬용, 없으면 null)
     * @param userLng    사용자 현재 경도
     * @param page       0부터 시작하는 페이지 번호
     * @param size       페이지 크기
     * @param email      로그인 회원 이메일 (즐겨찾기 표시용, 비로그인 null)
     */
    public List<PinSummaryResponse> search(String keyword, PinType typeFilter,
                                           Double userLat, Double userLng, int page, int size, String email) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        // 검색 대상 핀을 연관과 함께 한 번에 로딩 (카테고리/소속건물/층)
        List<Pin> candidates = pinRepository.findAllForSearch();
        Set<Long> favoriteIds = favoritePinIds(resolveMember(email));
        LocalDateTime now = LocalDateTime.now(KST);

        boolean hasGps = userLat != null && userLng != null;
        boolean insideCampus = distanceCalculator.isWithinCampus(userLat, userLng);

        // 1. 종류 필터 + 관련도 점수 계산 (0 이하는 매칭 실패로 제외)
        List<Scored> scored = new ArrayList<>();
        for (Pin pin : candidates) {
            if (typeFilter != null && pin.getType() != typeFilter) {
                continue;
            }
            double relevance = matcher.score(pin.getName(), pin.getCategory().getLabel(), keyword);
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
     * @param typeFilter 종류 필터 (null이면 전체)
     * @param limit      최대 개수 (0 이하면 10)
     */
    public List<MapSuggestResponse> suggest(String keyword, PinType typeFilter, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int safeLimit = limit > 0 ? limit : 10;

        return pinRepository.findAllForSearch().stream()
                .filter(pin -> typeFilter == null || pin.getType() == typeFilter)
                .map(pin -> new Scored(pin, matcher.score(pin.getName(), pin.getCategory().getLabel(), keyword), null))
                .filter(s -> s.relevance() > 0.0)
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed()
                        .thenComparing(s -> s.pin().getName()))
                .limit(safeLimit)
                .map(s -> MapSuggestResponse.from(s.pin()))
                .toList();
    }

    // ====================== 내부 헬퍼 ======================

    /** 핀 1개를 검색 결과 카드 DTO로 변환 */
    private PinSummaryResponse toSummary(Pin pin, Set<Long> favoriteIds,
                                         Double userLat, Double userLng, boolean insideCampus, LocalDateTime now) {
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
                operatingStatusLabel(pin, now),
                displayDistance(pin, userLat, userLng, insideCampus),
                pin.resolveLatitude(),
                pin.resolveLongitude());
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
        return new HashSet<>(pinFavoriteRepository.findFavoritePinIds(member));
    }
}
