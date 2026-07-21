package nova.mjs.domain.thingo.review.service.moderation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.service.ContentModerationPort;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 리뷰 신고 자동 숨김/복원 (L2).
 *
 * - 신고 도메인의 {@link ContentModerationPort} 구현: REVIEW 대상만 처리(다른 타입은 무시).
 *   신고 도메인은 여러 ContentModerationPort 구현을 모두 호출하고, 각 구현이 자기 타입만 담당한다.
 * - 운영자용 숨김 리뷰 목록 조회 + 복원.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewModerationService implements ContentModerationPort {

    private final ReviewRepository reviewRepository;

    /**
     * 신고 누적 임계 초과 시 리뷰 자동 숨김 (신고 트랜잭션 내 호출).
     * REVIEW 대상만 처리하고, 이미 숨김이거나 대상이 없으면 무시(멱등).
     */
    @Override
    @Transactional
    public void hideByReport(ReportTargetType targetType, UUID targetUuid) {
        if (targetType != ReportTargetType.REVIEW) {
            return; // 리뷰 외 타입은 다른 포트 구현이 담당
        }
        reviewRepository.findByUuid(targetUuid).ifPresent(Review::hideByReport);
        log.info("리뷰 신고 자동 숨김 - reviewUuid: {}", targetUuid);
    }

    /** 자동 숨김된 리뷰 목록 (운영자 검토 큐, 최신순) */
    @Transactional(readOnly = true)
    public List<ReviewDTO.Response.Summary> getHiddenReviews() {
        return reviewRepository.findByHiddenTrueOrderByCreatedAtDesc().stream()
                .map(review -> ReviewDTO.Response.Summary.from(review, false, false))
                .toList();
    }

    /** 리뷰 숨김 해제 (운영자 검토 후 복원) */
    @Transactional
    public void restoreReview(UUID reviewUuid) {
        Review review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(ReviewNotFoundException::new);
        review.restore();
        log.info("리뷰 숨김 해제 - reviewUuid: {}", reviewUuid);
    }
}
