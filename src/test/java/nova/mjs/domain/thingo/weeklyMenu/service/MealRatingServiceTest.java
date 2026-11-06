package nova.mjs.domain.thingo.weeklyMenu.service;

import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.weeklyMenu.DTO.MealRatingDTO;
import nova.mjs.domain.thingo.weeklyMenu.entity.MealRating;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import nova.mjs.domain.thingo.weeklyMenu.exception.WeeklyMenuException;
import nova.mjs.domain.thingo.weeklyMenu.repository.MealRatingRepository;
import nova.mjs.domain.thingo.weeklyMenu.repository.WeeklyMenuRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MealRatingServiceTest {

    private final MealRatingRepository mealRatingRepository = mock(MealRatingRepository.class);
    private final WeeklyMenuRepository weeklyMenuRepository = mock(WeeklyMenuRepository.class);
    private final MemberQueryService memberQueryService = mock(MemberQueryService.class);
    private final MealRatingService service =
            new MealRatingService(mealRatingRepository, weeklyMenuRepository, memberQueryService);

    @Test
    @DisplayName("평점이 없는 끼니도 count 0으로 조/중/석 3건을 돌려주고 평균은 소수 첫째 자리로 반올림한다")
    void should_세끼니_요약_when_일부_끼니만_평점() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(mealRatingRepository.summarize(date)).thenReturn(List.of(summary(MenuCategory.LUNCH, 4.266, 3L)));

        List<MealRatingDTO.Response.Summary> result = service.getSummaries(date, null);

        assertThat(result).extracting(MealRatingDTO.Response.Summary::getMenuCategory)
                .containsExactly(MenuCategory.BREAKFAST, MenuCategory.LUNCH, MenuCategory.DINNER);
        assertThat(result.get(0).getCount()).isZero();
        assertThat(result.get(1).getAverage()).isEqualTo(4.3);
        assertThat(result.get(1).getCount()).isEqualTo(3L);
        assertThat(result.get(1).getMyScore()).isNull();
    }

    @Test
    @DisplayName("오늘 식단이 없는 끼니에는 평점을 남길 수 없다")
    void should_예외_when_오늘_식단_없음() {
        when(weeklyMenuRepository.existsByDateStartingWithAndMenuCategory(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.rate("a@mju.ac.kr", new MealRatingDTO.Request.Rate(MenuCategory.LUNCH, 5)))
                .isInstanceOf(WeeklyMenuException.class);
        verify(mealRatingRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 끼니에 다시 평점을 남기면 새로 저장하지 않고 점수를 덮어쓴다")
    void should_점수_덮어쓰기_when_이미_평점_있음() {
        Member member = mock(Member.class);
        LocalDate today = service.today();
        MealRating existing = MealRating.create(member, today, MenuCategory.LUNCH, 2);
        when(weeklyMenuRepository.existsByDateStartingWithAndMenuCategory(anyString(), eq(MenuCategory.LUNCH)))
                .thenReturn(true);
        when(memberQueryService.getMemberByEmail("a@mju.ac.kr")).thenReturn(member);
        when(mealRatingRepository.findByMemberAndMealDateAndMenuCategory(member, today, MenuCategory.LUNCH))
                .thenReturn(Optional.of(existing));

        service.rate("a@mju.ac.kr", new MealRatingDTO.Request.Rate(MenuCategory.LUNCH, 5));

        assertThat(existing.getScore()).isEqualTo(5);
        verify(mealRatingRepository, never()).save(any());
    }

    private MealRatingRepository.Summary summary(MenuCategory category, double average, long count) {
        return new MealRatingRepository.Summary() {
            public MenuCategory getMenuCategory() { return category; }
            public Double getAverage() { return average; }
            public Long getCount() { return count; }
        };
    }
}
