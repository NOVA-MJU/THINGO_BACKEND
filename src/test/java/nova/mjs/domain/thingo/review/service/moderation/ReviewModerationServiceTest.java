package nova.mjs.domain.thingo.review.service.moderation;

import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewKeyword;
import nova.mjs.domain.thingo.review.exception.ReviewNotFoundException;
import nova.mjs.domain.thingo.review.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewModerationServiceTest {

    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private ReviewModerationService service;

    private Review 리뷰() {
        Member author = Member.builder()
                .uuid(UUID.randomUUID()).role(Member.Role.USER).name("t").nickname("길동")
                .email("e@mju.ac.kr").password("p").college(College.AI_SOFTWARE).build();
        CategoryGroup group = CategoryGroup.of("food", "식사", 1);
        Category category = Category.ofChip("restaurant", group, "음식점", null, null, null,
                CategoryResultType.PLACE_LIST, false, 1);
        Pin pin = Pin.ofExternalPlace("code", category, "장소", 37.0, 127.0, null, null, "주소");
        return Review.create(pin, author, "내용", Set.of(ReviewKeyword.KIND));
    }

    @Test
    @DisplayName("REVIEW 대상 신고 임계 도달 시 리뷰를 숨긴다")
    void should_hide_when_REVIEW() {
        Review review = 리뷰();
        UUID uuid = review.getUuid();
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.of(review));

        service.hideByReport(ReportTargetType.REVIEW, uuid);

        assertThat(review.isHidden()).isTrue();
    }

    @Test
    @DisplayName("REVIEW가 아닌 타입은 리뷰 리포지토리를 건드리지 않는다")
    void should_ignore_when_다른타입() {
        service.hideByReport(ReportTargetType.BOARD, UUID.randomUUID());

        verify(reviewRepository, never()).findByUuid(any());
    }

    @Test
    @DisplayName("복원하면 숨김이 해제된다")
    void should_restore() {
        Review review = 리뷰();
        review.hideByReport();
        UUID uuid = review.getUuid();
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.of(review));

        service.restoreReview(uuid);

        assertThat(review.isHidden()).isFalse();
    }

    @Test
    @DisplayName("없는 리뷰 복원 시 REVIEW_NOT_FOUND")
    void should_throw_when_복원대상_없음() {
        UUID uuid = UUID.randomUUID();
        given(reviewRepository.findByUuid(uuid)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreReview(uuid))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
