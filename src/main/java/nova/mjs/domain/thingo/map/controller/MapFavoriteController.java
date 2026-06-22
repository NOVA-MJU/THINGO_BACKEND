package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.service.PinFavoriteService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 명지도 즐겨찾기 컨트롤러.
 *
 * [제공 API]
 * - POST /api/v1/map/favorites - 건물/장소 즐겨찾기 토글 (로그인 필요)
 *
 * 건물·장소를 핀 ID 하나로 동일하게 다룬다(Pin 통합).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/map")
public class MapFavoriteController {

    private final PinFavoriteService pinFavoriteService;

    /**
     * 건물/장소 즐겨찾기 토글 (추가/해제).
     *
     * - 별을 누르면 등록, 다시 누르면 해제
     * - 즐겨찾기한 핀은 칩 목록에서 상단으로 정렬된다
     *
     * @param pinId 건물/장소 핀 ID
     * @return true: 즐겨찾기 추가됨 / false: 즐겨찾기 해제됨
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/favorites")
    public ApiResponse<Boolean> toggleFavorite(
            @RequestParam("pinId") Long pinId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[명지도 즐겨찾기 토글] pinId={}, email={}", pinId, userPrincipal.getUsername());
        boolean favorite = pinFavoriteService.toggleFavorite(userPrincipal.getUsername(), pinId);
        return ApiResponse.success(favorite);
    }
}
