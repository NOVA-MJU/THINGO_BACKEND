package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.service.MapPinService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 명지도 카테고리(칩) 컨트롤러.
 *
 * [제공 API]
 * 1. GET /api/v1/map/categories/{code}/pins  - 특정 칩 클릭 시 장소/건물 목록
 *
 * 전체 카테고리 목록/상단 퀵메뉴 구성은 프론트가 자체 보유(onClick 매핑)하므로 백엔드는 제공하지 않는다.
 *
 * [인증]
 * - 모두 비로그인 가능. 로그인 시 즐겨찾기 마킹/상단 정렬이 적용된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map")
public class MapCategoryController {

    private final MapPinService mapPinService;

    /**
     * 특정 칩을 눌렀을 때 보여줄 장소/건물 목록.
     *
     * - 정렬: 즐겨찾기 먼저 → 현재 위치에서 가까운 순 (캠퍼스 밖이면 정문 기준 정렬)
     * - 장소 목록은 무한 스크롤용으로 page/size로 잘라 반환한다 (건물 칩은 수가 적음)
     * - 버스 칩(result_type=BUS)은 빈 목록을 반환한다. 프론트는 result_type을 보고 버스 화면으로 이동한다.
     *
     * @param code 칩 코드 (예: "daedong", "printer")
     * @param lat  사용자 현재 위도 (없으면 거리 미계산)
     * @param lng  사용자 현재 경도
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기
     */
    @GetMapping("/categories/{code}/pins")
    public ApiResponse<List<PinSummaryResponse>> getPinsByCategory(
            @PathVariable("code") String code,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[명지도 칩 목록] code={}, lat={}, lng={}, page={}, email={}", code, lat, lng, page, email);
        return ApiResponse.success(mapPinService.getPinsByCategory(code, lat, lng, page, size, email));
    }
}
