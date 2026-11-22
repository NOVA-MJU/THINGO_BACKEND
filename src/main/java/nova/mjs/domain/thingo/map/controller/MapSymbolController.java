package nova.mjs.domain.thingo.map.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.map.dto.SymbolMatchResponse;
import nova.mjs.domain.thingo.map.dto.SymbolPlaceCreateRequest;
import nova.mjs.domain.thingo.map.service.MapSymbolMatchService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이버 지도 기본 글자(심벌) 대조 API.
 *
 * GET /api/v1/map/symbol-match?name=스타벅스 명지대점&lat=37.58&lng=126.92
 * 찾으면 핀(id, type, name, 좌표), 못 찾으면 data가 null이다. 앱은 null이면 가게 종류를 고르고 리뷰를 쓰는 시트를 연다.
 *
 * POST /api/v1/map/symbol-places {name, latitude, longitude, categoryCode}
 * 그 시트에서 리뷰 쓰기를 누르면 비슷한 핀을 돌려주거나 대동명지도 장소를 만든다 (로그인 필요).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map")
public class MapSymbolController {

    private final MapSymbolMatchService mapSymbolMatchService;

    @GetMapping("/symbol-match")
    public ApiResponse<SymbolMatchResponse> matchSymbol(
            @RequestParam("name") String name,
            @RequestParam("lat") double latitude,
            @RequestParam("lng") double longitude
    ) {
        return ApiResponse.success(mapSymbolMatchService.match(name, latitude, longitude).orElse(null));
    }

    @PostMapping("/symbol-places")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SymbolMatchResponse> createSymbolPlace(@Valid @RequestBody SymbolPlaceCreateRequest request) {
        return ApiResponse.success(mapSymbolMatchService.createPlace(request));
    }
}
