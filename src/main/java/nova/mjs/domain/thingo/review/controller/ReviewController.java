package nova.mjs.domain.thingo.review.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.service.command.ReviewCommandService;
import nova.mjs.domain.thingo.review.service.like.ReviewLikeService;
import nova.mjs.domain.thingo.review.service.query.ReviewQueryService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 명지도 장소 리뷰 API.
 * - 조회(목록/상세/미디어/키워드)는 비로그인 허용(로그인 시 isLiked/isMine 부가)
 * - 작성/삭제/좋아요는 로그인 필요
 */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;
    private final ReviewLikeService reviewLikeService;

    /** 리뷰 작성 */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDTO.Response.Detail>> createReview(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ReviewDTO.Request.Create request
    ) {
        ReviewDTO.Response.Detail response =
                reviewCommandService.createReview(userPrincipal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 장소별 리뷰 목록(최신순, 차단 사용자 제외) */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReviewDTO.Response.Summary>>> getReviews(
            @RequestParam Long pinId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = resolveEmail(userPrincipal);
        Pageable pageable = PageRequest.of(page, size);
        Page<ReviewDTO.Response.Summary> response = reviewQueryService.getReviews(pinId, pageable, email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 리뷰 단건 상세 */
    @GetMapping("/{reviewUuid}")
    public ResponseEntity<ApiResponse<ReviewDTO.Response.Detail>> getReview(
            @PathVariable UUID reviewUuid,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = resolveEmail(userPrincipal);
        ReviewDTO.Response.Detail response = reviewQueryService.getReview(reviewUuid, email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 리뷰 삭제(작성자/OPERATOR) */
    @DeleteMapping("/{reviewUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteReview(
            @PathVariable UUID reviewUuid,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        reviewCommandService.deleteReview(userPrincipal.getUsername(), reviewUuid);
        return ResponseEntity.noContent().build();
    }

    /** 좋아요 토글(알림 없음) */
    @PostMapping("/{reviewUuid}/like")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewDTO.Response.LikeResult>> toggleLike(
            @PathVariable UUID reviewUuid,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        ReviewDTO.Response.LikeResult response =
                reviewLikeService.toggleLike(reviewUuid, userPrincipal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 장소 상세 상단 사진·영상 스트립(최신순) */
    @GetMapping("/media")
    public ResponseEntity<ApiResponse<List<ReviewDTO.Response.MediaStripItem>>> getMediaStrip(
            @RequestParam Long pinId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = resolveEmail(userPrincipal);
        List<ReviewDTO.Response.MediaStripItem> response =
                reviewQueryService.getMediaStrip(pinId, limit, email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 작성 화면용 키워드 카탈로그(pinId가 F&B면 전용 키워드 포함, 생략 시 전체) */
    @GetMapping("/keywords")
    public ResponseEntity<ApiResponse<ReviewDTO.Response.KeywordCatalog>> getKeywordCatalog(
            @RequestParam(required = false) Long pinId
    ) {
        ReviewDTO.Response.KeywordCatalog response = reviewQueryService.getKeywordCatalog(pinId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String resolveEmail(UserPrincipal userPrincipal) {
        return userPrincipal != null ? userPrincipal.getUsername() : null;
    }
}
