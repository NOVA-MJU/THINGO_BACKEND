package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.config.MapThemeCatalog;
import nova.mjs.domain.thingo.map.dto.MapThemeResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.service.MapPinService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 명지도 테마지도 컨트롤러.
 *
 * [제공 API]
 * 1. GET /api/v1/map/themes                  - 검색 화면 하단 테마 카드 목록
 * 2. GET /api/v1/map/themes/{themeId}/pins   - 테마에 묶인 장소 목록 (카테고리 칩 목록과 같은 카드 형식)
 *
 * [인증]
 * - 비로그인 가능. 로그인 시 즐겨찾기 여부가 표시된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map/themes")
public class MapThemeController {

    private final MapThemeCatalog themeCatalog;
    private final MapPinService mapPinService;

    @GetMapping
    public ApiResponse<List<MapThemeResponse>> getThemes() {
        return ApiResponse.success(themeCatalog.themes().stream().map(MapThemeResponse::from).toList());
    }

    @GetMapping("/{themeId}/pins")
    public ApiResponse<List<PinSummaryResponse>> getThemePins(
            @PathVariable("themeId") String themeId,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 테마지도] themeId={}, lat={}, lng={}, email={}", themeId, lat, lng, email);
        MapThemeCatalog.Theme theme = themeCatalog.resolve(themeId);
        // 카테고리 칩 목록과 같은 지도 화면 흐름(장소 상세 시트)으로 열리도록 층별안내도 타입은 기본 장소로 낮춘다
        List<PinSummaryResponse> pins = mapPinService.getPinsByCodes(theme.placeCodes(), lat, lng, email);
        return ApiResponse.success(pins.stream().map(PinSummaryResponse::toPlaceFallback).toList());
    }
}
