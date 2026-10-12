package nova.mjs.domain.thingo.keywordAlarm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.NotificationHistoryDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.AlarmOwnerResolver;
import nova.mjs.domain.thingo.keywordAlarm.service.NotificationHistoryService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 알림함(키워드 알림 발송 내역) 컨트롤러.
 *
 * 로그인 회원은 JWT 로, 비로그인 사용자는 X-Device-Id 헤더로 알림함 소유자를 식별한다.
 *
 * - GET   /api/v1/notifications          - 내 알림 목록(최신순)
 * - GET   /api/v1/notifications/unread-status - 메인 화면 미읽음 배지 상태
 * - PATCH /api/v1/notifications/{id}/read - 단건 읽음
 * - PATCH /api/v1/notifications/read-all   - 전체 읽음
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;
    private final AlarmOwnerResolver alarmOwnerResolver;

    @GetMapping
    public ApiResponse<NotificationHistoryDTO.Response.Inbox> getMyNotifications(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        return ApiResponse.success(notificationHistoryService.getMyNotifications(
                alarmOwnerResolver.resolve(userPrincipal, deviceId), pageable));
    }

    @GetMapping("/unread-status")
    public ApiResponse<NotificationHistoryDTO.Response.UnreadStatus> getUnreadStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        return ApiResponse.success(notificationHistoryService.getUnreadStatus(
                alarmOwnerResolver.resolve(userPrincipal, deviceId)));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationHistoryDTO.Response.Detail> markAsRead(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        return ApiResponse.success(notificationHistoryService.markAsRead(
                alarmOwnerResolver.resolve(userPrincipal, deviceId), id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationHistoryDTO.Response.ReadAllResult> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = AlarmOwnerResolver.DEVICE_ID_HEADER, required = false) String deviceId
    ) {
        return ApiResponse.success(notificationHistoryService.markAllAsRead(
                alarmOwnerResolver.resolve(userPrincipal, deviceId)));
    }
}
