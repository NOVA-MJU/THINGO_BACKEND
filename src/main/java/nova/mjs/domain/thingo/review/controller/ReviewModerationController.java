package nova.mjs.domain.thingo.review.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.service.moderation.ReviewModerationService;
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
 * 신고 자동 숨김 리뷰 운영자 검토 API (L2).
 * 모든 엔드포인트 OPERATOR 권한 필요.
 */
@RestController
@RequestMapping("/api/v1/reviews/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class ReviewModerationController {

    private final ReviewModerationService moderationService;

    /** 자동 숨김된 리뷰 목록 */
    @GetMapping("/hidden")
    public ResponseEntity<ApiResponse<List<ReviewDTO.Response.Summary>>> getHiddenReviews() {
        return ResponseEntity.ok(ApiResponse.success(moderationService.getHiddenReviews()));
    }

    /** 리뷰 숨김 해제 */
    @PatchMapping("/{reviewUuid}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable UUID reviewUuid) {
        moderationService.restoreReview(reviewUuid);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
