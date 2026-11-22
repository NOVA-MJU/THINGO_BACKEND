package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.SymbolMatchResponse;
import nova.mjs.domain.thingo.map.dto.SymbolPlaceCreateRequest;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.exception.SymbolPlaceInvalidException;
import nova.mjs.domain.thingo.map.repository.CategoryRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.SymbolNameMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 네이버 지도 기본 글자(가게·건물 이름)를 우리 핀과 대조한다.
 *
 * 앱은 네이버 글자를 누르면 이름과 좌표를 보내고, 여기서 카테고리와 상관없이 모든 핀 중 이름이 비슷하고
 * 가까운 곳을 찾는다. 전수조사로 가게를 서버에 추가하면 앱을 다시 배포하지 않아도 바로 찾힌다.
 *
 * 우리 핀이 없는 가게도 바로 리뷰를 쓸 수 있게, 로그인한 사용자가 리뷰 쓰기를 누르면 그 글자로 장소를 만든다
 * ({@link #createPlace}). 리뷰를 쓰려는 사람이 있을 때만 만들어 지도를 한 번 누른 것만으로는 핀이 늘지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapSymbolMatchService {

    // 네이버 심벌 좌표와 우리 핀 좌표는 건물 대표점 차이로 수십 m 어긋날 수 있다
    static final double MATCH_METERS = 80;
    private static final double METERS_PER_DEGREE = 111_320;

    // 대동 상권(학교 앞 상가)까지 넉넉히. 멀리 있는 곳을 띵고 장소로 만들지 않게 막는다
    static final double SYMBOL_PLACE_MAX_METERS = 2_000;
    // 만든 가게는 대동명지도 하위 탭에 들어가 다른 가게처럼 목록·다녀온 곳·리뷰 키워드(F&B)를 그대로 쓴다
    private static final String SYMBOL_PLACE_PARENT_CATEGORY_CODE = "daedong";
    private static final String SYMBOL_PLACE_CODE_PREFIX = "symbol-";

    private final PinRepository pinRepository;
    private final CategoryRepository categoryRepository;
    private final DistanceCalculator distanceCalculator;

    public Optional<SymbolMatchResponse> match(String name, double latitude, double longitude) {
        return findSimilarPin(name, latitude, longitude).map(SymbolMatchResponse::from);
    }

    /**
     * 이름이 비슷한 핀이 근처에 있으면 그 핀을, 없으면 새 장소를 만들어 돌려준다.
     * 두 사람이 같은 가게를 동시에 누르는 정도의 중복은 드물어서 잠금 없이 대조 후 만든다.
     */
    @Transactional
    public SymbolMatchResponse createPlace(SymbolPlaceCreateRequest request) {
        String name = request.name().trim();
        Optional<Pin> similarPin = findSimilarPin(name, request.latitude(), request.longitude());
        if (similarPin.isPresent()) {
            return SymbolMatchResponse.from(similarPin.get());
        }

        double distanceFromCampus = distanceCalculator.distanceMeters(
                request.latitude(), request.longitude(), CampusArea.CENTER_LATITUDE, CampusArea.CENTER_LONGITUDE);
        if (distanceFromCampus > SYMBOL_PLACE_MAX_METERS) {
            throw new SymbolPlaceInvalidException("학교에서 너무 먼 곳입니다.");
        }
        Category category = categoryRepository.findByCode(request.categoryCode())
                .filter(found -> found.getParent() != null
                        && SYMBOL_PLACE_PARENT_CATEGORY_CODE.equals(found.getParent().getCode()))
                .orElseThrow(() -> new SymbolPlaceInvalidException("대동명지도 가게 종류가 아닙니다."));

        // 구글 시트·전수조사 동기화 code와 겹치지 않게 접두사를 붙인다
        String code = SYMBOL_PLACE_CODE_PREFIX + UUID.randomUUID();
        Pin place = pinRepository.save(Pin.ofExternalPlace(
                code, category, name, request.latitude(), request.longitude(), null, null, null));
        log.info("네이버 글자로 장소 추가 - pinId: {}, name: {}, category: {}",
                place.getId(), name, category.getCode());
        return SymbolMatchResponse.from(place);
    }

    private Optional<Pin> findSimilarPin(String name, double latitude, double longitude) {
        double latitudeDelta = MATCH_METERS / METERS_PER_DEGREE;
        double longitudeDelta = latitudeDelta / Math.cos(Math.toRadians(latitude));
        return pinRepository.findAllInBox(
                        latitude - latitudeDelta, latitude + latitudeDelta,
                        longitude - longitudeDelta, longitude + longitudeDelta)
                .stream()
                .filter(pin -> SymbolNameMatcher.isSimilarName(name, pin.getName()))
                .map(pin -> Map.entry(pin, distanceCalculator.distanceMeters(
                        latitude, longitude, pin.getLatitude(), pin.getLongitude())))
                .filter(pinWithDistance -> pinWithDistance.getValue() <= MATCH_METERS)
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }
}
