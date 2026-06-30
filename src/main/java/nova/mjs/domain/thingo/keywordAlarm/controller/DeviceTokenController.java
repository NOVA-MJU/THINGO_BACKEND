package nova.mjs.domain.thingo.keywordAlarm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceTokenDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceTokenService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * FCM 기기 토큰 컨트롤러.
 *
 * - POST   /api/v1/device-tokens - 토큰 등록(멱등) (로그인 필요)
 * - DELETE /api/v1/device-tokens - 토큰 삭제(로그아웃) (로그인 필요)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ApiResponse<Void> register(
            @Valid @RequestBody DeviceTokenDTO.Request.Register request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[FCM 토큰 등록] email={}, platform={}", userPrincipal.getUsername(), request.getPlatform());
        deviceTokenService.register(userPrincipal.getUsername(), request);
        return ApiResponse.success();
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping
    public ApiResponse<Void> delete(
            @RequestParam("fcmToken") String fcmToken,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[FCM 토큰 삭제] email={}", userPrincipal.getUsername());
        deviceTokenService.delete(userPrincipal.getUsername(), fcmToken);
        return ApiResponse.success();
    }
}
