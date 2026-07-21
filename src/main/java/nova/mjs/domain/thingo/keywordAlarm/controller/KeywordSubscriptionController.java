package nova.mjs.domain.thingo.keywordAlarm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.KeywordSubscriptionService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 키워드 알림 설정 컨트롤러 (화면 06-2-3).
 *
 * [제공 API]
 * - POST   /api/v1/keyword-alarms              - 키워드 등록 (로그인 필요)
 * - GET    /api/v1/keyword-alarms              - 내 키워드 목록 (로그인 필요)
 * - PATCH  /api/v1/keyword-alarms/{id}         - 키워드/카테고리 수정 (로그인 필요)
 * - PATCH  /api/v1/keyword-alarms/{id}/enabled - 알림 on/off (로그인 필요)
 * - DELETE /api/v1/keyword-alarms/{id}         - 키워드 삭제 (로그인 필요)
 * - GET    /api/v1/keyword-alarms/recommended  - 추천 키워드(고정값)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keyword-alarms")
public class KeywordSubscriptionController {

    private final KeywordSubscriptionService keywordSubscriptionService;

    /** 키워드 등록 (화면 4번 등록 버튼) */
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> create(
            @Valid @RequestBody KeywordSubscriptionDTO.Request.Create request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[키워드 알림 등록] email={}, keyword={}", userPrincipal.getUsername(), request.getKeyword());
        return ApiResponse.success(keywordSubscriptionService.create(userPrincipal.getUsername(), request));
    }

    /** 내 키워드 목록 (화면 06-2-4 등록 리스트) */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiResponse<List<KeywordSubscriptionDTO.Response.Detail>> getMySubscriptions(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ApiResponse.success(keywordSubscriptionService.getMySubscriptions(userPrincipal.getUsername()));
    }

    /** 키워드/카테고리 수정 (키워드 + 카테고리 전체 교체) */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}")
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody KeywordSubscriptionDTO.Request.Update request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[키워드 알림 수정] email={}, id={}", userPrincipal.getUsername(), id);
        return ApiResponse.success(keywordSubscriptionService.update(userPrincipal.getUsername(), id, request));
    }

    /** 키워드 알림 on/off (스위치) */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}/enabled")
    public ApiResponse<KeywordSubscriptionDTO.Response.Detail> updateEnabled(
            @PathVariable("id") Long id,
            @Valid @RequestBody KeywordSubscriptionDTO.Request.UpdateEnabled request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[키워드 알림 on/off] email={}, id={}, enabled={}", userPrincipal.getUsername(), id, request.getEnabled());
        return ApiResponse.success(keywordSubscriptionService.updateEnabled(userPrincipal.getUsername(), id, request));
    }

    /** 키워드 삭제 */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        log.info("[키워드 알림 삭제] email={}, id={}", userPrincipal.getUsername(), id);
        keywordSubscriptionService.delete(userPrincipal.getUsername(), id);
        return ApiResponse.success();
    }

    /** 추천 키워드 (화면 6번, 고정값 상시 노출) */
    @GetMapping("/recommended")
    public ApiResponse<List<String>> getRecommendedKeywords() {
        return ApiResponse.success(keywordSubscriptionService.getRecommendedKeywords());
    }
}
