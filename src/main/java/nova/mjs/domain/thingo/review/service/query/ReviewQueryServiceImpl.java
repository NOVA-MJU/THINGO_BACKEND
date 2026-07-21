package nova.mjs.domain.thingo.review.service.query;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.block.service.BlockQueryService;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.service.PinQueryService;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewMedia;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewLikeRepository;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 리뷰 조회 서비스 구현.
 * - 차단 사용자(BlockQueryService) 리뷰를 목록/상세/스트립에서 제외
 * - 로그인 시 isLiked(일괄 조회)/isMine 계산, 비로그인은 미적용
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryServiceImpl implements ReviewQueryService {

    private static final String FNB_GROUP_CODE = "food";
    /** 스트립용으로 미디어를 충분히 모으기 위해 최소로 훑어볼 리뷰 수 */
    private static final int MIN_REVIEW_SCAN = 20;

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberQueryService memberQueryService;
    private final BlockQueryService blockQueryService;
    private final PinQueryService pinQueryService;

    @Override
    public Page<ReviewDTO.Response.Summary> getReviews(Long pinId, Pageable pageable, String email) {
        Member viewer = resolveViewer(email);
        Long viewerId = viewer == null ? null : viewer.getId();
        Set<Long> hidden = blockQueryService.getHiddenMemberIds(viewerId);

        // 차단 대상 있으면 제외 쿼리, 없으면 기본 쿼리(빈 IN 방지). 자동숨김(hidden)은 항상 제외.
        Page<Review> page = hidden.isEmpty()
                ? reviewRepository.findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(pinId, pageable)
                : reviewRepository.findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(pinId, hidden, pageable);

        Set<UUID> likedUuids = resolveLikedUuids(viewer, page.getContent());
        return page.map(review -> ReviewDTO.Response.Summary.from(
                review, likedUuids.contains(review.getUuid()), isMine(review, viewerId)));
    }

    @Override
    public ReviewDTO.Response.Detail getReview(UUID reviewUuid, String email) {
        Member viewer = resolveViewer(email);
        Long viewerId = viewer == null ? null : viewer.getId();

        Review review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(ReviewNotFoundException::new);

        // 신고 누적 자동 숨김된 리뷰는 상세도 404로 숨긴다(운영자 API로만 접근)
        if (review.isHidden()) {
            throw new ReviewNotFoundException();
        }

        // 차단 관계(양방향)면 상세도 404로 숨긴다(게시판 정책과 동일)
        Set<Long> hidden = blockQueryService.getHiddenMemberIds(viewerId);
        if (hidden.contains(review.getAuthor().getId())) {
            throw new ReviewNotFoundException();
        }

        boolean liked = viewer != null && reviewLikeRepository.existsByMemberAndReview(viewer, review);
        boolean mine = isMine(review, viewerId);
        boolean canDelete = mine || (viewer != null && viewer.getRole() == Member.Role.OPERATOR);
        return ReviewDTO.Response.Detail.from(review, liked, mine, canDelete);
    }

    @Override
    public List<ReviewDTO.Response.MediaStripItem> getMediaStrip(Long pinId, int limit, String email) {
        Member viewer = resolveViewer(email);
        Long viewerId = viewer == null ? null : viewer.getId();
        Set<Long> hidden = blockQueryService.getHiddenMemberIds(viewerId);

        Pageable scan = PageRequest.of(0, Math.max(limit, MIN_REVIEW_SCAN));
        Page<Review> page = hidden.isEmpty()
                ? reviewRepository.findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(pinId, scan)
                : reviewRepository.findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(pinId, hidden, scan);

        // 최신 리뷰부터 미디어를 순서대로 평탄화, limit개까지
        List<ReviewDTO.Response.MediaStripItem> items = new ArrayList<>();
        for (Review review : page.getContent()) {
            List<ReviewMedia> ordered = review.getMedia().stream()
                    .sorted(Comparator.comparingInt(ReviewMedia::getSortOrder))
                    .toList();
            for (ReviewMedia media : ordered) {
                if (items.size() >= limit) {
                    return items;
                }
                items.add(ReviewDTO.Response.MediaStripItem.of(review.getUuid(), media));
            }
        }
        return items;
    }

    @Override
    public ReviewDTO.Response.KeywordCatalog getKeywordCatalog(Long pinId) {
        boolean isFnb;
        if (pinId == null) {
            isFnb = true; // 정적 전체 카탈로그
        } else {
            Pin pin = pinQueryService.getPinById(pinId);
            isFnb = FNB_GROUP_CODE.equals(pin.getCategory().getGroup().getCode());
        }
        return ReviewDTO.buildKeywordCatalog(isFnb);
    }

    // ===== 헬퍼 =====

    /** email이 null이면 비로그인(null 반환). 값이 있으면 회원 로딩 */
    private Member resolveViewer(String email) {
        return email == null ? null : memberQueryService.getMemberByEmail(email);
    }

    private boolean isMine(Review review, Long viewerId) {
        return viewerId != null && viewerId.equals(review.getAuthor().getId());
    }

    /** 목록 isLiked 일괄 계산: 뷰어가 좋아요한 리뷰 uuid 집합 */
    private Set<UUID> resolveLikedUuids(Member viewer, List<Review> reviews) {
        if (viewer == null || reviews.isEmpty()) {
            return Set.of();
        }
        List<UUID> uuids = reviews.stream().map(Review::getUuid).toList();
        return new HashSet<>(reviewLikeRepository.findLikedReviewUuids(viewer.getId(), uuids));
    }
}
