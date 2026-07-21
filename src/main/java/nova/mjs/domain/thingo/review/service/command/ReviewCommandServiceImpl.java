package nova.mjs.domain.thingo.review.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.service.PinQueryService;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewKeyword;
import nova.mjs.domain.thingo.review.entity.ReviewMedia;
import nova.mjs.domain.thingo.review.exception.ReviewForbiddenException;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.exception.ReviewValidationException;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.profanity.ProfanityFilter;
import nova.mjs.util.s3.S3Service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 리뷰 커맨드 서비스 구현.
 * - 리뷰 작성 가능 장소(PLACE + 동아리방/흡연부스 제외) 검증
 * - 키워드 개수(1~5)/조합(적절없음 단독)/카테고리 허용(F&B 전용) 검증
 * - 미디어 개수(≤10) 검증
 * - 삭제 시 미디어 S3 정리 후 하드 삭제
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewCommandServiceImpl implements ReviewCommandService {

    /** 리뷰 비활성 카테고리 코드(악용 방지: 동아리방/흡연부스) */
    private static final Set<String> REVIEW_DISABLED_CATEGORY_CODES = Set.of("club-room", "smoking");
    /** F&B(식사) 그룹 코드. F&B 전용 키워드 노출/검증 기준 */
    private static final String FNB_GROUP_CODE = "food";
    private static final int MAX_MEDIA = 10;

    private final ReviewRepository reviewRepository;
    private final MemberQueryService memberQueryService;
    private final PinQueryService pinQueryService;
    private final S3Service s3Service;
    private final ProfanityFilter profanityFilter;

    @Override
    @Transactional
    public ReviewDTO.Response.Detail createReview(String email, ReviewDTO.Request.Create request) {
        // 1. 작성자·장소 조회
        Member author = memberQueryService.getMemberByEmail(email);
        Pin pin = pinQueryService.getPinById(request.getPinId());

        // 2. 리뷰 작성 가능한 장소인지 검증
        validateReviewablePin(pin);

        // 3. 키워드 검증(개수/조합/카테고리 허용)
        Set<ReviewKeyword> keywords = validateKeywords(request.getKeywords(), pin);

        // 4. 미디어 개수 검증
        List<ReviewDTO.Request.MediaItem> mediaItems =
                request.getMedia() == null ? List.of() : request.getMedia();
        if (mediaItems.size() > MAX_MEDIA) {
            throw new ReviewValidationException(ErrorCode.REVIEW_MEDIA_LIMIT_EXCEEDED);
        }

        // 5. 리뷰 생성(비속어 마스킹 L1) + 미디어 순서대로 부착
        String maskedContent = profanityFilter.mask(request.getContent());
        Review review = Review.create(pin, author, maskedContent, keywords);
        mediaItems.forEach(item -> review.addMedia(item.getUrl(), item.getMediaType()));
        reviewRepository.save(review);

        log.info("리뷰 작성 완료 - reviewUuid: {}, pinId: {}, 작성자: {}",
                review.getUuid(), pin.getId(), author.getId());

        // 작성 직후: 본인 리뷰이므로 isMine/canDelete=true, 좋아요 0/false
        return ReviewDTO.Response.Detail.from(review, false, true, true);
    }

    @Override
    @Transactional
    public void deleteReview(String email, UUID reviewUuid) {
        Member member = memberQueryService.getMemberByEmail(email);
        Review review = reviewRepository.findByUuid(reviewUuid)
                .orElseThrow(ReviewNotFoundException::new);

        // 작성자 또는 OPERATOR만 삭제 가능
        boolean canDelete = review.isAuthoredBy(member) || member.getRole() == Member.Role.OPERATOR;
        if (!canDelete) {
            throw new ReviewForbiddenException();
        }

        // 미디어 S3 정리(best-effort) 후 하드 삭제(연관 media/like는 cascade/orphanRemoval)
        deleteMediaFromS3(review);
        reviewRepository.delete(review);

        log.info("리뷰 삭제 완료 - reviewUuid: {}, 요청자: {}", reviewUuid, member.getId());
    }

    // ===== 검증 =====

    /** 리뷰 작성 가능 장소: PLACE 타입 + 동아리방/흡연부스 제외 */
    private void validateReviewablePin(Pin pin) {
        if (pin.getType() != PinType.PLACE) {
            throw new ReviewValidationException(ErrorCode.REVIEW_NOT_ALLOWED_FOR_CATEGORY);
        }
        if (REVIEW_DISABLED_CATEGORY_CODES.contains(pin.getCategory().getCode())) {
            throw new ReviewValidationException(ErrorCode.REVIEW_NOT_ALLOWED_FOR_CATEGORY);
        }
    }

    /**
     * 키워드 검증 후 중복 제거된 집합을 반환한다.
     * - 개수 1~5
     * - '적절한 키워드 없음'은 단독 선택만 허용
     * - 비F&B 장소는 F&B 전용 키워드 선택 불가
     */
    private Set<ReviewKeyword> validateKeywords(List<ReviewKeyword> rawKeywords, Pin pin) {
        List<ReviewKeyword> list = rawKeywords == null ? List.of() : rawKeywords;
        Set<ReviewKeyword> keywords = new LinkedHashSet<>(list);

        int count = keywords.size();
        if (count < 1 || count > 5) {
            throw new ReviewValidationException(ErrorCode.REVIEW_KEYWORD_COUNT_INVALID);
        }
        if (keywords.contains(ReviewKeyword.NONE_APPROPRIATE) && count > 1) {
            throw new ReviewValidationException(ErrorCode.REVIEW_KEYWORD_COMBINATION_INVALID);
        }
        boolean isFnb = FNB_GROUP_CODE.equals(pin.getCategory().getGroup().getCode());
        if (!isFnb && keywords.stream().anyMatch(ReviewKeyword::isFbOnly)) {
            throw new ReviewValidationException(ErrorCode.REVIEW_KEYWORD_NOT_ALLOWED_FOR_CATEGORY);
        }
        return keywords;
    }

    /** 리뷰 미디어를 S3에서 개별 삭제한다(실패는 무시하고 로그). */
    private void deleteMediaFromS3(Review review) {
        for (ReviewMedia media : review.getMedia()) {
            try {
                s3Service.deleteFile(s3Service.replaceCloudfrontUrlToS3Url(media.getUrl()));
            } catch (Exception e) {
                log.warn("리뷰 미디어 S3 삭제 실패(무시) - url: {}, cause: {}", media.getUrl(), e.getMessage());
            }
        }
    }
}
