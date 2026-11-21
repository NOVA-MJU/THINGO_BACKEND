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
import nova.mjs.domain.thingo.review.entity.ReviewSort;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewLikeRepository;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.service.ReportQueryService;
import nova.mjs.domain.thingo.review.exception.ReviewValidationException;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int MAX_PAGE_SIZE = 50;
    private static final Long EMPTY_MEMBER_SENTINEL = -1L;
    private static final UUID EMPTY_REVIEW_SENTINEL = new UUID(0L, 0L);

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberQueryService memberQueryService;
    private final BlockQueryService blockQueryService;
    private final PinQueryService pinQueryService;
    private final ReportQueryService reportQueryService;

    @Override
    public ReviewDTO.Response.CursorPage getReviews(
            Long pinId, String sortRaw, String encodedCursor, int size, String email) {
        validatePageSize(size);
        ReviewSort sort = ReviewSort.fromApiValue(sortRaw);
        ReviewCursor cursor = ReviewCursorCodec.decode(encodedCursor, sort);

        Member viewer = resolveViewer(email);
        Long viewerId = viewer == null ? null : viewer.getId();
        Visibility visibility = resolveVisibility(viewerId);

        // size+1 조회로 다음 페이지 유무를 판정한다. 별도의 count는 화면의 리뷰 총 개수 표시에만 사용한다.
        PageRequest fetchSize = PageRequest.of(0, size + 1);
        List<Review> fetched = sort == ReviewSort.LATEST
                ? reviewRepository.findLatestCursor(
                        pinId, visibility.authorIds(), visibility.reviewUuids(),
                        cursor.createdAt(), cursor.reviewId(), fetchSize)
                : reviewRepository.findLikesCursor(
                        pinId, visibility.authorIds(), visibility.reviewUuids(),
                        cursor.likeCount(), cursor.createdAt(), cursor.reviewId(), fetchSize);

        boolean hasNext = fetched.size() > size;
        List<Review> reviews = hasNext ? fetched.subList(0, size) : fetched;
        Set<UUID> likedUuids = resolveLikedUuids(viewer, reviews);
        List<ReviewDTO.Response.Summary> content = reviews.stream()
                .map(review -> ReviewDTO.Response.Summary.from(
                        review, likedUuids.contains(review.getUuid()), isMine(review, viewerId)))
                .toList();

        String nextCursor = hasNext && !reviews.isEmpty()
                ? ReviewCursorCodec.encode(reviews.get(reviews.size() - 1), sort)
                : null;
        long totalElements = reviewRepository.countVisible(
                pinId, visibility.authorIds(), visibility.reviewUuids());

        return ReviewDTO.Response.CursorPage.builder()
                .content(content)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .size(content.size())
                .totalElements(totalElements)
                .sort(sort.getApiValue())
                .build();
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
        Visibility visibility = resolveVisibility(viewerId);
        if (visibility.authorIds().contains(review.getAuthor().getId())
                || visibility.reviewUuids().contains(review.getUuid())) {
            throw new ReviewNotFoundException();
        }

        boolean liked = viewer != null && reviewLikeRepository.existsByMemberAndReview(viewer, review);
        boolean mine = isMine(review, viewerId);
        boolean canDelete = mine || (viewer != null && viewer.getRole() == Member.Role.OPERATOR);
        return ReviewDTO.Response.Detail.from(review, liked, mine, canDelete);
    }

    @Override
    public List<ReviewDTO.Response.MediaStripItem> getMediaStrip(Long pinId, int limit, String email) {
        validatePageSize(limit);
        Member viewer = resolveViewer(email);
        Long viewerId = viewer == null ? null : viewer.getId();
        Visibility visibility = resolveVisibility(viewerId);

        return reviewRepository.findVisibleMedia(
                        pinId, visibility.authorIds(), visibility.reviewUuids(),
                        PageRequest.of(0, limit))
                .stream()
                .map(media -> ReviewDTO.Response.MediaStripItem.of(
                        media.getReview().getUuid(), media))
                .toList();
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

    @Override
    public List<ReviewDTO.Response.MyReview> getMyReviews(String email) {
        Member viewer = resolveViewer(email);
        List<Review> reviews =
                reviewRepository.findByAuthor_IdAndHiddenFalseOrderByCreatedAtDesc(viewer.getId());
        Set<UUID> likedUuids = resolveLikedUuids(viewer, reviews);
        return reviews.stream()
                .map(review -> ReviewDTO.Response.MyReview.from(
                        review, likedUuids.contains(review.getUuid())))
                .toList();
    }

    // ===== 헬퍼 =====

    /** email이 null이면 비로그인(null 반환). 값이 있으면 회원 로딩 */
    private Member resolveViewer(String email) {
        return email == null ? null : memberQueryService.getMemberByEmail(email);
    }

    private boolean isMine(Review review, Long viewerId) {
        return viewerId != null && viewerId.equals(review.getAuthor().getId());
    }

    private Visibility resolveVisibility(Long viewerId) {
        Set<Long> hiddenAuthors = blockQueryService.getHiddenMemberIds(viewerId);
        Set<UUID> selfReported = reportQueryService.getSelfReportedTargetUuids(
                viewerId, ReportTargetType.REVIEW);
        return new Visibility(
                hiddenAuthors == null || hiddenAuthors.isEmpty()
                        ? Set.of(EMPTY_MEMBER_SENTINEL) : hiddenAuthors,
                selfReported == null || selfReported.isEmpty()
                        ? Set.of(EMPTY_REVIEW_SENTINEL) : selfReported);
    }

    private void validatePageSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ReviewValidationException(ErrorCode.REVIEW_PAGE_SIZE_INVALID);
        }
    }

    private record Visibility(Set<Long> authorIds, Set<UUID> reviewUuids) {
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
