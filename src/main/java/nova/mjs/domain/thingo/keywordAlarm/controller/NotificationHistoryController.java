package nova.mjs.domain.thingo.keywordAlarm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.NotificationHistoryDTO;
import nova.mjs.domain.thingo.keywordAlarm.service.NotificationHistoryService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 알림함(키워드 알림 발송 내역) 컨트롤러.
 *
 * - GET   /api/v1/notifications          - 내 알림 목록(최신순)
 * - PATCH /api/v1/notifications/{id}/read - 단건 읽음
 * - PATCH /api/v1/notifications/read-all   - 전체 읽음
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ApiResponse<Page<NotificationHistoryDTO.Response.Detail>> getMyNotifications(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ApiResponse.success(
                notificationHistoryService.getMyNotifications(userPrincipal.getUsername(), pageable));
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        notificationHistoryService.markAsRead(userPrincipal.getUsername(), id);
        return ApiResponse.success();
    }

    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ApiResponse.success(notificationHistoryService.markAllAsRead(userPrincipal.getUsername()));
    }
}
