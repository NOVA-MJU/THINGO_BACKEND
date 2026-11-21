package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.map.dto.SymbolMatchResponse;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.SymbolNameMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 네이버 지도 기본 글자(가게·건물 이름)를 우리 핀과 대조한다.
 *
 * 앱은 네이버 글자를 누르면 이름과 좌표를 보내고, 여기서 카테고리와 상관없이 모든 핀 중 이름이 비슷하고
 * 가까운 곳을 찾는다. 전수조사로 가게를 서버에 추가하면 앱을 다시 배포하지 않아도 바로 찾힌다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapSymbolMatchService {

    // 네이버 심벌 좌표와 우리 핀 좌표는 건물 대표점 차이로 수십 m 어긋날 수 있다
    static final double MATCH_METERS = 80;
    private static final double METERS_PER_DEGREE = 111_320;

    private final PinRepository pinRepository;
    private final DistanceCalculator distanceCalculator;

    public Optional<SymbolMatchResponse> match(String name, double latitude, double longitude) {
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
                .map(pinWithDistance -> SymbolMatchResponse.from(pinWithDistance.getKey()));
    }
}
