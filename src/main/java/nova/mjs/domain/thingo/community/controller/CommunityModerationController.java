package nova.mjs.domain.thingo.community.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardResponse;
import nova.mjs.domain.thingo.community.comment.DTO.CommentResponseDto;
import nova.mjs.domain.thingo.community.service.CommunityModerationService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 신고 자동 숨김 콘텐츠 운영자 검토 API (L2).
 *
 * - 모든 엔드포인트는 OPERATOR 권한 필요.
 * - 숨김된 게시글/댓글 목록 확인 후 정상 콘텐츠면 복원한다.
 */
@RestController
@RequestMapping("/api/v1/community/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class CommunityModerationController {

    private final CommunityModerationService moderationService;

    /** 자동 숨김된 게시글 목록 */
    @GetMapping("/hidden/boards")
    public ResponseEntity<ApiResponse<List<CommunityBoardResponse.SummaryDTO>>> getHiddenBoards() {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getHiddenBoards()));
    }

    /** 자동 숨김된 댓글 목록 */
    @GetMapping("/hidden/comments")
    public ResponseEntity<ApiResponse<List<CommentResponseDto.CommentSummaryDto>>> getHiddenComments() {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getHiddenComments()));
    }

    /** 게시글 숨김 해제 */
    @PatchMapping("/boards/{uuid}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreBoard(@PathVariable UUID uuid) {
        moderationService.restoreBoard(uuid);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 댓글 숨김 해제 */
    @PatchMapping("/comments/{uuid}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreComment(@PathVariable UUID uuid) {
        moderationService.restoreComment(uuid);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
