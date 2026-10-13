package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.service.MapSearchService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 명지도 특화 검색 컨트롤러.
 *
 * 기존 통합검색(/api/v1/search)과 분리된 명지도(건물/장소) 전용 검색이다.
 *
 * [제공 API]
 * 1. GET /api/v1/map/search           - 검색 결과 목록 (무한 스크롤)
 * 2. GET /api/v1/map/search/suggest   - 검색 자동완성 (경량)
 *
 * [공통 파라미터]
 * - keyword: 검색어 (실내 title 정확일치 → 라벨 정확일치 → 일반 이름 검색 순으로 판정)
 * - lat/lng: 프론트 GPS 좌표. 거리 계산/정렬에만 쓰고 저장하지 않는다
 *
 * [인증]
 * - 비로그인 가능. 로그인 시 즐겨찾기 여부가 표시된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map")
public class MapSearchController {

    private final MapSearchService mapSearchService;

    /**
     * 명지도 검색 결과 조회.
     * data의 각 항목 type(BUILDING/PLACE/FLOOR_MAP)으로 화면 이동 방식을 결정한다.
     */
    @GetMapping("/search")
    public ApiResponse<List<PinSummaryResponse>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "seed", required = false) String seed,
            @RequestParam(value = "floorMap", defaultValue = "false") boolean floorMap,
            @RequestParam(value = "strict", defaultValue = "false") boolean strict,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 검색] keyword={}, lat={}, lng={}, page={}, floorMap={}, strict={}, email={}",
                keyword, lat, lng, page, floorMap, strict, email);
        List<PinSummaryResponse> results =
                mapSearchService.search(keyword, lat, lng, page, size, email, seed, floorMap, strict);
        return ApiResponse.success(
                floorMap ? results : results.stream().map(PinSummaryResponse::toPlaceFallback).toList());
    }

    /**
     * 명지도 검색 자동완성 조회.
     *
     * @param floorMap 기본 false면 내부 시설도 type=PLACE·link=null. true일 때만 FLOOR_MAP 라우팅을 제공한다.
     */
    @GetMapping("/search/suggest")
    public ApiResponse<List<MapSuggestResponse>> suggest(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "floorMap", defaultValue = "false") boolean floorMap
    ) {
        log.info("[명지도 자동완성] keyword={}, limit={}, floorMap={}", keyword, limit, floorMap);
        List<MapSuggestResponse> suggestions = mapSearchService.suggest(keyword, limit);
        return ApiResponse.success(
                floorMap ? suggestions : suggestions.stream().map(MapSuggestResponse::toPlaceFallback).toList());
    }
}
