package nova.mjs.domain.thingo.review.service.like;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewLike;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewLikeRepository;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 리뷰 좋아요 토글. 게시판 CommunityLikeService와 동일 동작.
 * 정책상 좋아요 알림(FCM)은 없다(알림은 학교 공식 정보 전용).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewLikeService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberQueryService memberQueryService;

    /**
     * 좋아요 토글. 없으면 추가(+1), 있으면 취소(-1). 결과 상태와 최신 좋아요 수 반환.
     */
    @Transactional
    public ReviewDTO.Response.LikeResult toggleLike(UUID reviewUuid, String email) {
        Member member = memberQueryService.getMemberByEmail(email);
        Review review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(ReviewNotFoundException::new);

        Optional<ReviewLike> existing = reviewLikeRepository.findByMemberAndReview(member, review);
        boolean liked;
        if (existing.isPresent()) {
            // 이미 좋아요 → 취소
            reviewLikeRepository.delete(existing.get());
            reviewRepository.decreaseLikeCount(reviewUuid);
            liked = false;
        } else {
            // 좋아요 추가
            reviewLikeRepository.save(new ReviewLike(member, review));
            reviewRepository.increaseLikeCount(reviewUuid);
            liked = true;
        }

        int likeCount = reviewRepository.findLikeCount(reviewUuid);
        return ReviewDTO.Response.LikeResult.builder()
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }
}
