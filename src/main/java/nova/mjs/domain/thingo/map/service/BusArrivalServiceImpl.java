package nova.mjs.domain.thingo.map.service;

import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.BusArrivalResponse;
import nova.mjs.domain.thingo.map.config.BusStationCatalog;
import nova.mjs.domain.thingo.map.dto.SeoulBusArrivalApiDto;
import nova.mjs.domain.thingo.map.exception.BusArrivalApiCallException;
import nova.mjs.domain.thingo.map.exception.BusArrivalException;
import nova.mjs.domain.thingo.map.exception.BusArrivalParseException;
import nova.mjs.domain.thingo.map.repository.BusFavoriteRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 버스 도착 정보 서비스 구현체
 *
 * [작동 방식]
 * 1. 클라이언트가 arsId를 전달하면 공공데이터포털 API를 호출
 * 2. JSON 응답을 SeoulBusArrivalApiDto로 역직렬화
 * 3. itemList를 BusArrivalResponse.BusItem 목록으로 변환 (혼잡도 코드 → 한글 변환 포함)
 * 4. 정류장 메타 정보(이름, 위경도)는 application.yml 설정에서 매핑
 * 5. DB 저장 없이 매 요청마다 외부 API를 실시간으로 호출
 *
 * [의존성]
 * - seoulBusApiClient: WebClient (WebClientConfig에서 Bean 등록)
 * - application.yml: bus.api.service-key, bus.stations.a/b 설정 필요
 */
@Slf4j
@Service
public class BusArrivalServiceImpl implements BusArrivalService {

    private final WebClient webClient;
    private final BusFavoriteRepository busFavoriteRepository;
    private final MemberRepository memberRepository;
    private final BusStationCatalog stationCatalog;

    /** 공공데이터포털 발급 서비스키 (URL 인코딩된 키 사용) */
    @Value("${bus.api.service-key}")
    private String serviceKey;

    /**
     * 혼잡도 코드 → 한글 매핑 테이블
     * - 공공데이터 API congestion 필드 값 기준
     * - "0"(정보없음)은 매핑하지 않고 null 처리
     */
    private static final Map<String, String> CONGESTION_MAP = Map.of(
            "3", "여유",
            "4", "보통",
            "5", "혼잡",
            "6", "매우혼잡"
    );

    /** arrmsg "[n번째 전]"에서 정류장 수를 추출하는 패턴 */
    private static final Pattern STATION_COUNT_PATTERN = Pattern.compile("\\[(\\d+)번째 전\\]");

    /** 도착 예측이 없는 상태를 나타내는 메시지 키워드 */
    private static final Set<String> NO_ARRIVAL_KEYWORDS = Set.of("출발대기", "운행종료");

    public BusArrivalServiceImpl(@Qualifier("seoulBusApiClient") WebClient webClient,
                                 BusFavoriteRepository busFavoriteRepository,
                                 MemberRepository memberRepository,
                                 BusStationCatalog stationCatalog) {
        this.webClient = webClient;
        this.busFavoriteRepository = busFavoriteRepository;
        this.memberRepository = memberRepository;
        this.stationCatalog = stationCatalog;
    }

    /**
     * 정류장 키(A/B)의 실시간 버스 도착 정보를 공공데이터 API에서 조회
     *
     * - 프론트는 정류장 키만 전달하고, 실제 arsId는 백엔드 카탈로그에서 해석한다
     *
     * [API 엔드포인트]
     * GET http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid
     *   ?serviceKey={serviceKey}&arsId={arsId}&resultType=json
     *
     * @param stationKey 정류장 선택 키 ("A" 또는 "B")
     * @param email      로그인 회원 이메일 (비로그인 시 null)
     * @return 정류장 정보 및 버스 도착 목록
     */
    @Override
    public BusArrivalResponse getArrivalsByStation(String stationKey, String email) {
        // 0. 정류장 키 → 실제 정류장 정보(arsId 포함) 해석
        BusStationCatalog.Station station = stationCatalog.resolve(stationKey);
        String arsId = station.getArsId();
        log.info("[버스 도착 정보 조회] station={}, arsId={}, email={}", stationKey, arsId, email);

        // 1. 공공데이터포털 API 호출
        SeoulBusArrivalApiDto rawResponse = callBusApi(arsId);

        // 2. 응답 구조 유효성 검증
        validateResponse(rawResponse, arsId);

        // 3. 로그인 사용자의 해당 정류장 즐겨찾기 노선 집합 조회 (비로그인 시 빈 집합)
        Set<String> favoriteRoutes = resolveFavoriteRoutes(email, arsId);

        // 4. itemList → BusItem 목록으로 변환 (즐겨찾기 여부 마킹)
        List<BusArrivalResponse.BusItem> buses = rawResponse.getMsgBody()
                .getItemList()
                .stream()
                .map(item -> toDto(item, favoriteRoutes))
                .collect(Collectors.toList());

        // 5. 즐겨찾기 노선을 상단으로 정렬 (그 외 순서는 API 응답 순서 유지 - 안정 정렬)
        buses.sort(Comparator.comparing(BusArrivalResponse.BusItem::isFavorite).reversed());

        return BusArrivalResponse.builder()
                .arsId(arsId)
                .stationName(station.getName())
                .lat(station.getLat())
                .lng(station.getLng())
                .buses(buses)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private 헬퍼 메서드
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 공공데이터포털 버스 도착 정보 API 호출
     * - WebClient 동기 호출 (.block()) 사용
     * - 호출 실패 시 BusArrivalApiCallException 발생
     */
    private SeoulBusArrivalApiDto callBusApi(String arsId) {
        try {
            String uri = String.format(
                    "/getStationByUid?serviceKey=%s&arsId=%s&resultType=json",
                    serviceKey, arsId
            );

            SeoulBusArrivalApiDto response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(SeoulBusArrivalApiDto.class)
                    .block();

            log.info("[버스 API 호출 성공] arsId={}", arsId);
            return response;

        } catch (Exception e) {
            log.error("[버스 API 호출 실패] arsId={}, 원인={}", arsId, e.getMessage(), e);
            throw new BusArrivalApiCallException();
        }
    }

    /**
     * API 응답 유효성 검증
     * - null 체크 및 빈 itemList 확인
     * - itemList가 null 또는 비어있으면 NOT_FOUND 예외 발생
     */
    private void validateResponse(SeoulBusArrivalApiDto response, String arsId) {
        try {
            // headerCd "7" = 키 인증 실패, "0" 외 = 오류로 처리
            String headerCd = response.getMsgHeader().getHeaderCd();
            if (!"0".equals(headerCd)) {
                log.error("[버스 API 오류 응답] arsId={}, headerCd={}, headerMsg={}",
                        arsId, headerCd, response.getMsgHeader().getHeaderMsg());
                throw new BusArrivalApiCallException();
            }

            List<SeoulBusArrivalApiDto.Item> items = response
                    .getMsgBody()
                    .getItemList();

            if (items == null || items.isEmpty()) {
                log.warn("[버스 도착 정보 없음] arsId={}", arsId);
                throw new BusArrivalException(ErrorCode.BUS_ARRIVAL_NOT_FOUND);
            }

        } catch (BusArrivalException e) {
            throw e; // 이미 처리된 예외는 그대로 전파
        } catch (Exception e) {
            log.error("[버스 응답 파싱 실패] arsId={}, 원인={}", arsId, e.getMessage(), e);
            throw new BusArrivalParseException();
        }
    }

    /**
     * 로그인 회원의 특정 정류장 즐겨찾기 노선 집합을 조회한다.
     * - email이 null이거나 회원을 찾지 못하면 빈 집합 반환 (비로그인/익명 허용)
     */
    private Set<String> resolveFavoriteRoutes(String email, String arsId) {
        if (email == null) {
            return Set.of();
        }
        return memberRepository.findByEmail(email)
                .map(member -> Set.copyOf(busFavoriteRepository.findRouteNamesByMemberAndArsId(member, arsId)))
                .orElseGet(Set::of);
    }

    /**
     * API 원본 Item → BusItem DTO 변환
     * - 노선 번호, 방면, 막차 시간 매핑
     * - 1·2번째 도착 정보를 Arrival 목록으로 변환 (도착 예측 없는 항목은 제외)
     * - favoriteRoutes에 노선 번호가 포함되면 즐겨찾기로 표시
     */
    BusArrivalResponse.BusItem toDto(SeoulBusArrivalApiDto.Item item, Set<String> favoriteRoutes) {
        List<BusArrivalResponse.Arrival> arrivals = new ArrayList<>();

        // 첫 번째 도착 정보
        BusArrivalResponse.Arrival first = toArrival(
                item.getArrmsg1(), item.getTraTime1(), item.getIsArrive1(), item.getCongestion1());
        if (first != null) {
            arrivals.add(first);
        }

        // 두 번째 도착 정보
        BusArrivalResponse.Arrival second = toArrival(
                item.getArrmsg2(), item.getTraTime2(), item.getIsArrive2(), item.getCongestion2());
        if (second != null) {
            arrivals.add(second);
        }

        return BusArrivalResponse.BusItem.builder()
                .routeName(item.getRtNm())
                .direction(item.getAdirection())
                .lastBusTime(formatLastBusTime(item.getLastTm()))
                .favorite(favoriteRoutes.contains(item.getRtNm()))
                .arrivals(arrivals)
                .build();
    }

    /**
     * 개별 도착 정보(arrmsg, traTime, congestion) → Arrival 변환
     * - 출발대기 / 운행종료 등 도착 예측이 없으면 null 반환 (목록에서 제외)
     */
    private BusArrivalResponse.Arrival toArrival(String arrmsg, String traTime, String isArrive, String congestionCode) {
        // 도착 예측이 없는 경우(빈 메시지, 출발대기, 운행종료)는 제외
        if (arrmsg == null || arrmsg.isBlank() || NO_ARRIVAL_KEYWORDS.contains(arrmsg.trim())) {
            return null;
        }

        return BusArrivalResponse.Arrival.builder()
                .remainingTime(formatRemainingTime(arrmsg, traTime, isArrive))
                .stationCount(parseStationCount(arrmsg))
                .congestion(convertCongestion(congestionCode))
                .build();
    }

    /**
     * 도착까지 남은 시간을 "m분 s초" 형식으로 변환
     * - 도착 임박("곧 도착") 또는 isArrive=="1"이면 "곧 도착" 반환
     * - traTime(초)을 분/초로 분해 (60초 미만은 "s초")
     * - traTime 파싱 불가 시 원본 arrmsg 반환
     */
    private String formatRemainingTime(String arrmsg, String traTime, String isArrive) {
        if ("1".equals(isArrive) || (arrmsg != null && arrmsg.contains("곧 도착"))) {
            return "곧 도착";
        }
        try {
            int totalSeconds = Integer.parseInt(traTime.trim());
            if (totalSeconds <= 0) {
                return arrmsg.trim();
            }
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return minutes > 0 ? minutes + "분 " + seconds + "초" : seconds + "초";
        } catch (NumberFormatException | NullPointerException e) {
            return arrmsg.trim();
        }
    }

    /**
     * arrmsg "[n번째 전]"에서 남은 정류장 수를 추출 (예: "2정류장")
     * - 패턴 미일치 시 null 반환
     */
    private String parseStationCount(String arrmsg) {
        Matcher matcher = STATION_COUNT_PATTERN.matcher(arrmsg);
        return matcher.find() ? matcher.group(1) + "정류장" : null;
    }

    /**
     * 막차 시간(HHMM, 공백 패딩 포함) → "HH:mm" 변환
     * - 예: "2240  " → "22:40"
     * - 형식이 맞지 않으면 null 반환
     */
    private String formatLastBusTime(String lastTm) {
        if (lastTm == null) {
            return null;
        }
        String trimmed = lastTm.trim();
        if (trimmed.length() != 4) {
            return null;
        }
        return trimmed.substring(0, 2) + ":" + trimmed.substring(2, 4);
    }

    /**
     * 혼잡도 코드를 한글 레이블로 변환
     * - 매핑 테이블에 없는 값(정보없음 "0" 등)은 null 반환
     *
     * @param code API 응답 congestion 값 ("3"~"6")
     * @return 한글 혼잡도 레이블 또는 null
     */
    private String convertCongestion(String code) {
        return CONGESTION_MAP.get(code);
    }
}
