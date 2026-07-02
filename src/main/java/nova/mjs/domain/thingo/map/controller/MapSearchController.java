package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.entity.PinType;
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
 * - keyword: 검색어 (건물/장소명 + 카테고리명 매칭, 초성/오타 허용)
 * - type: 종류 필터 (BUILDING/PLACE). 없으면 전체. 잘못된 값도 전체로 처리
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
     * 명지도 검색 결과 목록 조회 (무한 스크롤).
     * 반환 개수가 size보다 작으면 마지막 페이지다.
     */
    @GetMapping("/search")
    public ApiResponse<List<PinSummaryResponse>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        PinType typeFilter = parseType(type);
        log.info("[명지도 검색] keyword={}, type={}, lat={}, lng={}, page={}, email={}",
                keyword, typeFilter, lat, lng, page, email);
        return ApiResponse.success(mapSearchService.search(keyword, typeFilter, lat, lng, page, size, email));
    }

    /**
     * 명지도 검색 자동완성 조회.
     */
    @GetMapping("/search/suggest")
    public ApiResponse<List<MapSuggestResponse>> suggest(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        log.info("[명지도 자동완성] keyword={}, type={}, limit={}", keyword, type, limit);
        return ApiResponse.success(mapSearchService.suggest(keyword, parseType(type), limit));
    }

    /** type 파라미터를 PinType으로 파싱. 비었거나 잘못된 값이면 null(전체) */
    private PinType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return PinType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
