package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.BuildingDetailResponse;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.dto.PlaceDetailResponse;
import nova.mjs.domain.thingo.map.service.MapPinService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 명지도 건물/장소 컨트롤러.
 *
 * [제공 API]
 * 1. GET /api/v1/map/buildings             - 건물 전체 목록 (바텀시트)
 * 2. GET /api/v1/map/buildings/{id}        - 건물 상세 (운영시간/카테고리 탭/층별 시설)
 * 3. GET /api/v1/map/places/{id}           - 장소(비건물) 상세
 *
 * [위치 파라미터]
 * - lat/lng는 프론트가 기기 GPS로 얻어 전달한다. 거리 계산/정렬에만 쓰이고 저장하지 않는다.
 * - 캠퍼스 반경(500m) 밖이거나 좌표가 없으면 거리는 표시하지 않는다.
 *
 * [인증]
 * - 모두 비로그인 가능. 로그인 시 즐겨찾기 여부가 표시된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map")
public class MapPinController {

    private final MapPinService mapPinService;

    /**
     * 건물 전체 목록 조회 (건물 번호 순). 페이지네이션 없음.
     */
    @GetMapping("/buildings")
    public ApiResponse<List<PinSummaryResponse>> getBuildings(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 건물 목록] lat={}, lng={}, email={}", lat, lng, email);
        return ApiResponse.success(mapPinService.getBuildings(lat, lng, email));
    }

    /**
     * 건물 상세 조회.
     *
     * @param buildingId 건물 핀 ID
     */
    @GetMapping("/buildings/{buildingId}")
    public ApiResponse<BuildingDetailResponse> getBuildingDetail(
            @PathVariable("buildingId") Long buildingId,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 건물 상세] buildingId={}, email={}", buildingId, email);
        return ApiResponse.success(mapPinService.getBuildingDetail(buildingId, lat, lng, email));
    }

    /**
     * 장소(비건물) 상세 조회.
     *
     * @param placeId 장소 핀 ID
     */
    @GetMapping("/places/{placeId}")
    public ApiResponse<PlaceDetailResponse> getPlaceDetail(
            @PathVariable("placeId") Long placeId,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 장소 상세] placeId={}, email={}", placeId, email);
        return ApiResponse.success(mapPinService.getPlaceDetail(placeId, lat, lng, email));
    }
}
