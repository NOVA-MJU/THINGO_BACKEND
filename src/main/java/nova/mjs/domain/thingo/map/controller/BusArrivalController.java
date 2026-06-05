package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.BusArrivalResponse;
import nova.mjs.domain.thingo.map.service.BusArrivalService;
import nova.mjs.domain.thingo.map.service.BusFavoriteService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 버스 도착 정보 컨트롤러
 *
 * [제공 API]
 * 1. GET  /api/v1/bus/arrivals   - 정류장(A/B)의 실시간 버스 도착 정보 반환
 * 2. POST /api/v1/bus/favorites  - 정류장(A/B)의 특정 노선 즐겨찾기 토글 (로그인 필요)
 *
 * [정류장 선택]
 * - 프론트는 정류장 키("A"/"B")만 전달한다. 실제 ARS ID는 백엔드가 보유/해석한다.
 *
 * [인증]
 * - arrivals: 인증 불필요 (공개). 로그인 시 즐겨찾기 마킹/정렬 적용
 * - favorites: 로그인 필요
 *
 * [응답 형식]
 * - 공통 래퍼 ApiResponse<T>로 감싸서 반환
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bus")
public class BusArrivalController {

    private final BusArrivalService busArrivalService;
    private final BusFavoriteService busFavoriteService;

    /**
     * 정류장(A/B)의 실시간 버스 도착 정보 조회
     *
     * - 공공데이터포털 서울 버스 도착 정보 API를 실시간으로 호출
     * - 로그인 사용자의 경우 즐겨찾기한 노선이 상단에 정렬되고 favorite=true로 표시됨
     *
     * @param station       정류장 선택 키 ("A" 또는 "B")
     * @param userPrincipal 로그인 사용자 (비로그인 시 null)
     * @return 정류장 정보 및 버스 도착 목록
     */
    @GetMapping("/arrivals")
    public ApiResponse<BusArrivalResponse> getArrivals(
            @RequestParam("station") String station,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;
        log.info("[버스 도착 정보 요청] station={}, email={}", station, email);
        return ApiResponse.success(busArrivalService.getArrivalsByStation(station, email));
    }

    /**
     * 버스 노선 즐겨찾기 토글 (추가/해제)
     *
     * - 정류장의 특정 노선에 별을 누르면 등록, 다시 누르면 해제
     * - 로그인 필요
     *
     * @param station       정류장 선택 키 ("A" 또는 "B")
     * @param routeName     버스 노선 번호 (예: 7611)
     * @param userPrincipal 로그인 사용자
     * @return true: 즐겨찾기 추가됨 / false: 즐겨찾기 해제됨
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/favorites")
    public ApiResponse<Boolean> toggleFavorite(
            @RequestParam("station") String station,
            @RequestParam("routeName") String routeName,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[버스 즐겨찾기 토글] station={}, route={}, email={}", station, routeName, userPrincipal.getUsername());
        boolean favorite = busFavoriteService.toggleFavorite(userPrincipal.getUsername(), station, routeName);
        return ApiResponse.success(favorite);
    }
}
