package nova.mjs.domain.thingo.keywordAlarm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceTokenDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.AlarmOwnerResolver;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceService;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceTokenService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * FCM 기기 토큰 컨트롤러.
 *
 * - POST   /api/v1/device-tokens - 토큰 등록(멱등) (로그인 또는 X-Device-Id)
 *     로그인: 회원에게 연결(+ 유효한 X-Device-Id 가 있으면 함께 기록)
 *     비로그인: X-Device-Id 필수, 회원 연결을 끊고 게스트 토큰으로 등록(로그아웃 후 게스트 전환)
 * - DELETE /api/v1/device-tokens - 토큰 삭제(로그아웃) (로그인 필요)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final DeviceService deviceService;
    private final AlarmOwnerResolver alarmOwnerResolver;

    @PostMapping
    public ApiResponse<Void> register(
            @Valid @RequestBody DeviceTokenDTO.Request.Register request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceIdHeader
    ) {
        if (userPrincipal != null) {
            // 로그인 상태에서는 헤더가 틀려도 등록을 막지 않는다(구버전 앱 호환). 유효할 때만 기록.
            UUID deviceId = deviceService.findIssued(deviceIdHeader).orElse(null);
            log.info("[FCM 토큰 등록] email={}, platform={}, deviceId={}",
                    userPrincipal.getUsername(), request.getPlatform(), deviceId);
            deviceTokenService.register(userPrincipal.getUsername(), deviceId, request);
        } else {
            UUID deviceId = alarmOwnerResolver.requireDevice(deviceIdHeader);
            log.info("[FCM 토큰 게스트 등록] deviceId={}, platform={}", deviceId, request.getPlatform());
            deviceTokenService.registerGuest(deviceId, request);
        }
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
