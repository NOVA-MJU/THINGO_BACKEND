package nova.mjs.domain.thingo.review.service.query;

import nova.mjs.domain.thingo.block.service.BlockQueryService;
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
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewLikeRepository;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private MemberQueryService memberQueryService;
    @Mock private BlockQueryService blockQueryService;
    @Mock private PinQueryService pinQueryService;

    @InjectMocks private ReviewQueryServiceImpl service;

    // ===== 픽스처 =====

    private Member 회원(Long id, Member.Role role) {
        Member member = Member.builder()
                .uuid(UUID.randomUUID()).role(role).name("테스터").nickname("길동")
                .email("e@mju.ac.kr").password("p").college(College.AI_SOFTWARE).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Pin 장소(String categoryCode, String groupCode) {
        CategoryGroup group = CategoryGroup.of(groupCode, "그룹", 1);
        Category category = Category.ofChip(categoryCode, group, "라벨", null, null, null,
                CategoryResultType.PLACE_LIST, false, 1);
        return Pin.ofExternalPlace("code", category, "장소", 37.0, 127.0, null, null, "주소");
    }

    private Review 리뷰(Member author) {
        return Review.create(장소("restaurant", "food"), author, "내용", Set.of(ReviewKeyword.KIND));
    }

    private Review 리뷰_미디어2(Member author) {
        Review review = 리뷰(author);
        review.addMedia("https://thingo.kr/1.png", ReviewMediaType.IMAGE);
        review.addMedia("https://thingo.kr/2.mp4", ReviewMediaType.VIDEO);
        return review;
    }

    // ===== 목록 =====

    @Test
    @DisplayName("목록에서 isLiked/isMine이 계산된다")
    void should_계산_isLiked_isMine() {
        // given - 뷰어(1)의 리뷰 r1, 타인(2)의 리뷰 r2
        Member viewer = 회원(1L, Member.Role.USER);
        Member other = 회원(2L, Member.Role.USER);
        Review r1 = 리뷰(viewer);
        Review r2 = 리뷰(other);
        given(memberQueryService.getMemberByEmail("v@mju.ac.kr")).willReturn(viewer);
        given(blockQueryService.getHiddenMemberIds(1L)).willReturn(Set.of());
        given(reviewRepository.findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(eq(10L), any()))
                .willReturn(new PageImpl<>(List.of(r1, r2)));
        given(reviewLikeRepository.findLikedReviewUuids(eq(1L), anyList()))
                .willReturn(List.of(r1.getUuid()));

        // when
        Page<ReviewDTO.Response.Summary> page = service.getReviews(10L, PageRequest.of(0, 10), "v@mju.ac.kr");

        // then
        List<ReviewDTO.Response.Summary> content = page.getContent();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).isLiked()).isTrue();
        assertThat(content.get(0).isMine()).isTrue();
        assertThat(content.get(1).isLiked()).isFalse();
        assertThat(content.get(1).isMine()).isFalse();
    }

    @Test
    @DisplayName("차단 대상이 있으면 제외 쿼리를 사용한다")
    void should_use_excluding_query_when_차단존재() {
        Member viewer = 회원(1L, Member.Role.USER);
        given(memberQueryService.getMemberByEmail("v@mju.ac.kr")).willReturn(viewer);
        given(blockQueryService.getHiddenMemberIds(1L)).willReturn(Set.of(2L));
        given(reviewRepository.findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(eq(10L), anySet(), any()))
                .willReturn(new PageImpl<>(List.of(리뷰(viewer))));
        given(reviewLikeRepository.findLikedReviewUuids(eq(1L), anyList())).willReturn(List.of());

        service.getReviews(10L, PageRequest.of(0, 10), "v@mju.ac.kr");

        verify(reviewRepository).findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(eq(10L), anySet(), any());
        verify(reviewRepository, never()).findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("비로그인 목록은 차단 필터·isLiked 없이 조회된다")
    void should_목록_비로그인() {
        given(blockQueryService.getHiddenMemberIds(null)).willReturn(Set.of());
        given(reviewRepository.findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(eq(10L), any()))
                .willReturn(new PageImpl<>(List.of(리뷰(회원(2L, Member.Role.USER)))));

        Page<ReviewDTO.Response.Summary> page = service.getReviews(10L, PageRequest.of(0, 10), null);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).isLiked()).isFalse();
        assertThat(page.getContent().get(0).isMine()).isFalse();
    }

    // ===== 상세 =====

    @Test
    @DisplayName("차단 관계인 작성자의 리뷰 상세는 REVIEW_NOT_FOUND")
    void should_throw_when_차단관계_상세() {
        Member viewer = 회원(1L, Member.Role.USER);
        Member blocked = 회원(2L, Member.Role.USER);
        Review review = 리뷰(blocked);
        given(memberQueryService.getMemberByEmail("v@mju.ac.kr")).willReturn(viewer);
        given(reviewRepository.findByUuid(review.getUuid())).willReturn(Optional.of(review));
        given(blockQueryService.getHiddenMemberIds(1L)).willReturn(Set.of(2L));

        assertThatThrownBy(() -> service.getReview(review.getUuid(), "v@mju.ac.kr"))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    @DisplayName("OPERATOR가 조회하면 타인 리뷰라도 canDelete=true")
    void should_canDelete_when_operator() {
        Member operator = 회원(9L, Member.Role.OPERATOR);
        Member author = 회원(2L, Member.Role.USER);
        Review review = 리뷰(author);
        given(memberQueryService.getMemberByEmail("op@mju.ac.kr")).willReturn(operator);
        given(reviewRepository.findByUuid(review.getUuid())).willReturn(Optional.of(review));
        given(blockQueryService.getHiddenMemberIds(9L)).willReturn(Set.of());
        given(reviewLikeRepository.existsByMemberAndReview(operator, review)).willReturn(false);

        ReviewDTO.Response.Detail detail = service.getReview(review.getUuid(), "op@mju.ac.kr");

        assertThat(detail.isCanDelete()).isTrue();
        assertThat(detail.isMine()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 상세는 REVIEW_NOT_FOUND")
    void should_throw_when_상세_없음() {
        UUID uuid = UUID.randomUUID();
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReview(uuid, null))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    // ===== 미디어 스트립 =====

    @Test
    @DisplayName("사진·영상 스트립은 최신 리뷰부터 평탄화되어 limit개까지 반환된다")
    void should_스트립_평탄화_limit() {
        Member author = 회원(2L, Member.Role.USER);
        Review r1 = 리뷰_미디어2(author); // 2개
        Review r2 = 리뷰_미디어2(author); // 2개
        given(blockQueryService.getHiddenMemberIds(null)).willReturn(Set.of());
        given(reviewRepository.findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(eq(10L), any()))
                .willReturn(new PageImpl<>(List.of(r1, r2)));

        List<ReviewDTO.Response.MediaStripItem> strip = service.getMediaStrip(10L, 3, null);

        // r1 미디어 2개 + r2 미디어 1개 = 3개에서 컷
        assertThat(strip).hasSize(3);
        assertThat(strip.get(0).getReviewUuid()).isEqualTo(r1.getUuid());
        assertThat(strip.get(2).getReviewUuid()).isEqualTo(r2.getUuid());
    }

    // ===== 키워드 카탈로그 =====

    @Test
    @DisplayName("F&B 장소 카탈로그는 F&B 전용 키워드를 포함한다")
    void should_카탈로그_FnB_포함() {
        given(pinQueryService.getPinById(1L)).willReturn(장소("restaurant", "food"));

        ReviewDTO.Response.KeywordCatalog catalog = service.getKeywordCatalog(1L);

        assertThat(catalog.isFnb()).isTrue();
        var foodGroup = catalog.getGroups().stream()
                .filter(g -> g.getGroup().equals("FOOD_PRICE")).findFirst().orElseThrow();
        assertThat(foodGroup.getKeywords()).hasSize(6);
    }

    @Test
    @DisplayName("비F&B 장소 카탈로그는 F&B 전용 키워드를 제외한다")
    void should_카탈로그_비FnB_제외() {
        given(pinQueryService.getPinById(2L)).willReturn(장소("lounge", "study"));

        ReviewDTO.Response.KeywordCatalog catalog = service.getKeywordCatalog(2L);

        assertThat(catalog.isFnb()).isFalse();
        var foodGroup = catalog.getGroups().stream()
                .filter(g -> g.getGroup().equals("FOOD_PRICE")).findFirst().orElseThrow();
        assertThat(foodGroup.getKeywords()).isEmpty(); // 전부 fbOnly라 제외

        var etcGroup = catalog.getGroups().stream()
                .filter(g -> g.getGroup().equals("ETC")).findFirst().orElseThrow();
        assertThat(etcGroup.getKeywords()).extracting(ReviewDTO.Response.KeywordTag::getCode)
                .doesNotContain("ADULT_MEAL"); // 어른 식사 대접은 fbOnly
    }
}
