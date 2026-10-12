package nova.mjs.domain.thingo.keywordAlarm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.service.AlarmOwnerResolver;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceService;
import nova.mjs.domain.thingo.keywordAlarm.service.KeywordSubscriptionService;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 키워드 알림 설정 컨트롤러 (화면 06-2-3).
 *
 * 로그인 회원은 JWT 로, 비로그인 사용자는 X-Device-Id(POST /api/v1/devices 로 발급) 헤더로 소유자를 식별한다.
 * 둘 다 없으면 401(DEVICE_ID_INVALID).
 *
 * [제공 API]
 * - POST   /api/v1/keyword-alarms              - 키워드 등록 (로그인 또는 X-Device-Id)
 * - GET    /api/v1/keyword-alarms              - 내 키워드 목록 (로그인 또는 X-Device-Id)
 * - PATCH  /api/v1/keyword-alarms/{id}         - 키워드/카테고리 수정 (로그인 또는 X-Device-Id)
 * - PATCH  /api/v1/keyword-alarms/{id}/enabled - 알림 on/off (로그인 또는 X-Device-Id)
 * - DELETE /api/v1/keyword-alarms/{id}         - 키워드 삭제 (로그인 또는 X-Device-Id)
 * - POST   /api/v1/keyword-alarms/merge-device - 로그인 직후 기기 구독/알림함을 회원으로 병합 (로그인 + X-Device-Id)
 * - GET    /api/v1/keyword-alarms/recommended  - 추천 키워드(고정값)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keyword-alarms")
public class KeywordSubscriptionController {

    private final KeywordSubscriptionService keywordSubscriptionService;
    private final AlarmOwnerResolver alarmOwnerResolver;
    private final DeviceService deviceService;

    /** 키워드 등록 (화면 4번 등록 버튼) */
    @PostMapping
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> create(
            @Valid @RequestBody KeywordSubscriptionDTO.Request.Create request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        AlarmOwner owner = alarmOwnerResolver.resolve(userPrincipal, deviceId);
        log.info("[키워드 알림 등록] owner={}, keyword={}", owner, request.getKeyword());
        return ApiResponse.success(keywordSubscriptionService.create(owner, request));
    }

    /** 내 키워드 목록 (화면 06-2-4 등록 리스트) */
    @GetMapping
    public ApiResponse<List<KeywordSubscriptionDTO.Response.Detail>> getMySubscriptions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        return ApiResponse.success(keywordSubscriptionService.getMySubscriptions(
                alarmOwnerResolver.resolve(userPrincipal, deviceId)));
    }

    /** 키워드/카테고리 수정 (키워드 + 카테고리 전체 교체) */
    @PatchMapping("/{id}")
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody KeywordSubscriptionDTO.Request.Update request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        AlarmOwner owner = alarmOwnerResolver.resolve(userPrincipal, deviceId);
        log.info("[키워드 알림 수정] owner={}, id={}", owner, id);
        return ApiResponse.success(keywordSubscriptionService.update(owner, id, request));
    }

    /** 키워드 알림 on/off (스위치) */
    @PatchMapping("/{id}/enabled")
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> updateEnabled(
            @PathVariable("id") Long id,
            @Valid @RequestBody KeywordSubscriptionDTO.Request.UpdateEnabled request,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        AlarmOwner owner = alarmOwnerResolver.resolve(userPrincipal, deviceId);
        log.info("[키워드 알림 on/off] owner={}, id={}, enabled={}", owner, id, request.getEnabled());
        return ApiResponse.success(keywordSubscriptionService.updateEnabled(owner, id, request));
    }

    /** 키워드 삭제 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        AlarmOwner owner = alarmOwnerResolver.resolve(userPrincipal, deviceId);
        log.info("[키워드 알림 삭제] owner={}, id={}", owner, id);
        keywordSubscriptionService.delete(owner, id);
        return ApiResponse.success();
    }

    /**
     * 로그인 직후 호출: 비로그인 상태에서 이 기기로 등록한 구독과 알림함을 회원으로 옮긴다.
     * 옮길 것이 없으면 0 건으로 응답한다(재호출 안전).
     * 서버가 모르는 기기 id 도 "옮길 것 없음"으로 본다 - 로그인 요청에 401 을 주면 앱이 토큰 만료로 오인할 수 있다.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/merge-device")
    public ApiResponse<KeywordSubscriptionDTO.Response.MergeResult> mergeDevice(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(AlarmOwnerResolver.DEVICE_ID_HEADER) String deviceIdHeader
    ) {
        Member member = alarmOwnerResolver.findMember(userPrincipal.getUsername());
        Optional<UUID> deviceId = deviceService.findIssued(deviceIdHeader);
        log.info("[키워드 알림 기기 병합] email={}, deviceId={}", userPrincipal.getUsername(), deviceId.orElse(null));
        return ApiResponse.success(deviceId
                .map(id -> keywordSubscriptionService.mergeDevice(member, id))
                .orElseGet(KeywordSubscriptionDTO.Response.MergeResult::empty));
    }

    /** 추천 키워드 (화면 6번, 고정값 상시 노출) */
    @GetMapping("/recommended")
    public ApiResponse<List<String>> getRecommendedKeywords() {
        return ApiResponse.success(keywordSubscriptionService.getRecommendedKeywords());
    }
}
