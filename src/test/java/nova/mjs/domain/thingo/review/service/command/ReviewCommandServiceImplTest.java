package nova.mjs.domain.thingo.review.service.command;

import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.service.PinQueryService;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewKeyword;
import nova.mjs.domain.thingo.review.entity.ReviewMediaType;
import nova.mjs.domain.thingo.review.exception.ReviewForbiddenException;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.exception.ReviewValidationException;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.profanity.ProfanityFilter;
import nova.mjs.util.s3.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewCommandServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private MemberQueryService memberQueryService;
    @Mock private PinQueryService pinQueryService;
    @Mock private S3Service s3Service;
    @Mock private ProfanityFilter profanityFilter;

    @InjectMocks private ReviewCommandServiceImpl service;

    // ===== 픽스처 =====

    private Member 회원(Long id, Member.Role role) {
        Member member = Member.builder()
                .uuid(UUID.randomUUID()).role(role).name("테스터").nickname("길동")
                .email("e@mju.ac.kr").password("p").college(College.AI_SOFTWARE).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Pin 장소(String categoryCode, String groupCode, PinType type) {
        CategoryGroup group = CategoryGroup.of(groupCode, "그룹", 1);
        Category category = Category.ofChip(categoryCode, group, "라벨", null, null, null,
                CategoryResultType.PLACE_LIST, false, 1);
        if (type == PinType.BUILDING) {
            return Pin.ofBuilding("code", category, "건물", 37.0, 127.0, null, null, 1, "S1XXX");
        }
        return Pin.ofExternalPlace("code", category, "장소", 37.0, 127.0, null, null, "주소");
    }

    private ReviewDTO.Request.MediaItem 미디어(String url, ReviewMediaType type) {
        ReviewDTO.Request.MediaItem item = new ReviewDTO.Request.MediaItem();
        ReflectionTestUtils.setField(item, "url", url);
        ReflectionTestUtils.setField(item, "mediaType", type);
        return item;
    }

    private ReviewDTO.Request.Create 요청(Long pinId, List<ReviewKeyword> keywords, String content,
                                         List<ReviewDTO.Request.MediaItem> media) {
        ReviewDTO.Request.Create request = new ReviewDTO.Request.Create();
        ReflectionTestUtils.setField(request, "pinId", pinId);
        ReflectionTestUtils.setField(request, "keywords", keywords);
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "media", media);
        return request;
    }

    // ===== 생성 =====

    @Test
    @DisplayName("유효한 요청이면 리뷰를 저장하고 본인 리뷰 상세를 반환한다")
    void should_create_when_유효한_요청() {
        // given - F&B 장소
        Member author = 회원(1L, Member.Role.USER);
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(author);
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        given(profanityFilter.mask(any())).willAnswer(invocation -> invocation.getArgument(0));
        var request = 요청(1L, List.of(ReviewKeyword.TASTY, ReviewKeyword.VALUE), "가성비 최고",
                List.of(미디어("https://thingo.kr/a.png", ReviewMediaType.IMAGE)));

        // when
        ReviewDTO.Response.Detail detail = service.createReview("e@mju.ac.kr", request);

        // then
        verify(reviewRepository).save(any(Review.class));
        assertThat(detail.isMine()).isTrue();
        assertThat(detail.isCanDelete()).isTrue();
        assertThat(detail.getKeywords()).hasSize(2);
        assertThat(detail.getMedia()).hasSize(1);
    }

    @Test
    @DisplayName("장소가 건물(BUILDING)이면 리뷰를 작성할 수 없다")
    void should_throw_when_건물() {
        // given
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("engineering", "food", PinType.BUILDING));
        var request = 요청(1L, List.of(ReviewKeyword.TASTY), "내용", null);

        // when & then
        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED_FOR_CATEGORY));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("동아리방(club-room) 카테고리는 리뷰를 작성할 수 없다")
    void should_throw_when_동아리방() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("club-room", "study", PinType.PLACE));
        var request = 요청(1L, List.of(ReviewKeyword.KIND), "내용", null);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_ALLOWED_FOR_CATEGORY));
    }

    @Test
    @DisplayName("키워드가 0개면 개수 검증 실패")
    void should_throw_when_키워드_0개() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        var request = 요청(1L, List.of(), "내용", null);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_KEYWORD_COUNT_INVALID));
    }

    @Test
    @DisplayName("키워드가 6개면 개수 검증 실패")
    void should_throw_when_키워드_6개() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        var request = 요청(1L, List.of(ReviewKeyword.TASTY, ReviewKeyword.VALUE, ReviewKeyword.FRESH,
                ReviewKeyword.GENEROUS, ReviewKeyword.NOT_BAD, ReviewKeyword.REVISIT), "내용", null);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_KEYWORD_COUNT_INVALID));
    }

    @Test
    @DisplayName("'적절한 키워드 없음'을 다른 키워드와 함께 선택하면 조합 검증 실패")
    void should_throw_when_none_조합() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        var request = 요청(1L, List.of(ReviewKeyword.NONE_APPROPRIATE, ReviewKeyword.KIND), "내용", null);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_KEYWORD_COMBINATION_INVALID));
    }

    @Test
    @DisplayName("비F&B 장소에서 F&B 전용 키워드를 선택하면 카테고리 허용 검증 실패")
    void should_throw_when_비FnB_fbOnly키워드() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("lounge", "study", PinType.PLACE));
        var request = 요청(1L, List.of(ReviewKeyword.TASTY), "내용", null);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_KEYWORD_NOT_ALLOWED_FOR_CATEGORY));
    }

    @Test
    @DisplayName("비F&B 장소라도 공통 키워드는 작성 가능")
    void should_create_when_비FnB_공통키워드() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("lounge", "study", PinType.PLACE));
        given(profanityFilter.mask(any())).willAnswer(invocation -> invocation.getArgument(0));
        var request = 요청(1L, List.of(ReviewKeyword.FOCUS, ReviewKeyword.KIND), "조용하고 좋아요", null);

        ReviewDTO.Response.Detail detail = service.createReview("e@mju.ac.kr", request);

        verify(reviewRepository).save(any(Review.class));
        assertThat(detail.getKeywords()).hasSize(2);
    }

    @Test
    @DisplayName("작성 시 본문의 비속어가 마스킹되어 저장된다")
    void should_mask_비속어_when_작성() {
        // given
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        given(profanityFilter.mask("씨발 맛있어요")).willReturn("** 맛있어요");
        var request = 요청(1L, List.of(ReviewKeyword.TASTY), "씨발 맛있어요", null);

        // when
        ReviewDTO.Response.Detail detail = service.createReview("e@mju.ac.kr", request);

        // then - 마스킹된 본문이 그대로 반영
        assertThat(detail.getContent()).isEqualTo("** 맛있어요");
    }

    @Test
    @DisplayName("미디어가 11개면 개수 초과 검증 실패")
    void should_throw_when_미디어_초과() {
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food", PinType.PLACE));
        List<ReviewDTO.Request.MediaItem> media = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            media.add(미디어("https://thingo.kr/" + i + ".png", ReviewMediaType.IMAGE));
        }
        var request = 요청(1L, List.of(ReviewKeyword.TASTY), "내용", media);

        assertThatThrownBy(() -> service.createReview("e@mju.ac.kr", request))
                .isInstanceOfSatisfying(ReviewValidationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REVIEW_MEDIA_LIMIT_EXCEEDED));
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("작성자는 자신의 리뷰를 삭제할 수 있고 미디어는 S3에서 정리된다")
    void should_delete_when_작성자() {
        // given
        Member author = 회원(1L, Member.Role.USER);
        Pin pin = 장소("restaurant", "food", PinType.PLACE);
        Review review = Review.create(pin, author, "내용", java.util.Set.of(ReviewKeyword.TASTY));
        review.addMedia("https://thingo.kr/a.png", ReviewMediaType.IMAGE);
        UUID uuid = review.getUuid();
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(author);
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.of(review));
        given(s3Service.replaceCloudfrontUrlToS3Url("https://thingo.kr/a.png")).willReturn("static/images/reviews/a.png");

        // when
        service.deleteReview("e@mju.ac.kr", uuid);

        // then
        verify(s3Service).deleteFile("static/images/reviews/a.png");
        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("OPERATOR는 타인의 리뷰를 삭제할 수 있다")
    void should_delete_when_operator() {
        Member author = 회원(1L, Member.Role.USER);
        Member operator = 회원(9L, Member.Role.OPERATOR);
        Review review = Review.create(장소("restaurant", "food", PinType.PLACE), author, "내용",
                java.util.Set.of(ReviewKeyword.TASTY));
        UUID uuid = review.getUuid();
        given(memberQueryService.getMemberByEmail("op@mju.ac.kr")).willReturn(operator);
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.of(review));

        service.deleteReview("op@mju.ac.kr", uuid);

        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("작성자도 OPERATOR도 아니면 삭제 권한 없음")
    void should_throw_when_타인삭제() {
        Member author = 회원(1L, Member.Role.USER);
        Member other = 회원(2L, Member.Role.USER);
        Review review = Review.create(장소("restaurant", "food", PinType.PLACE), author, "내용",
                java.util.Set.of(ReviewKeyword.TASTY));
        UUID uuid = review.getUuid();
        given(memberQueryService.getMemberByEmail("other@mju.ac.kr")).willReturn(other);
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deleteReview("other@mju.ac.kr", uuid))
                .isInstanceOf(ReviewForbiddenException.class);
        verify(reviewRepository, never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 삭제 시 REVIEW_NOT_FOUND")
    void should_throw_when_삭제대상_없음() {
        UUID uuid = UUID.randomUUID();
        given(memberQueryService.getMemberByEmail("e@mju.ac.kr")).willReturn(회원(1L, Member.Role.USER));
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteReview("e@mju.ac.kr", uuid))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
