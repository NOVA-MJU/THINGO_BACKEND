package nova.mjs.domain.thingo.weeklyMenu.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.domain.thingo.weeklyMenu.DTO.MealRatingDTO;
import nova.mjs.domain.thingo.weeklyMenu.entity.MealRating;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import nova.mjs.domain.thingo.weeklyMenu.exception.WeeklyMenuException;
import nova.mjs.domain.thingo.weeklyMenu.repository.MealRatingRepository;
import nova.mjs.domain.thingo.weeklyMenu.repository.WeeklyMenuRepository;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 학식 평점. 평점은 오늘(KST) 끼니에만 남길 수 있고, 같은 끼니에 다시 남기면 점수를 덮어쓴다.
 * 조회는 비로그인도 가능하며 로그인하면 내 점수를 함께 내려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MealRatingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MENU_DATE_PREFIX = DateTimeFormatter.ofPattern("MM.dd");

    private final MealRatingRepository mealRatingRepository;
    private final WeeklyMenuRepository weeklyMenuRepository;
    private final MemberQueryService memberQueryService;

    public LocalDate today() {
        return LocalDate.now(KST);
    }

    /** 날짜의 조/중/석 평점 요약. 평점이 없는 끼니도 count 0으로 항상 3건을 돌려준다 */
    public List<MealRatingDTO.Response.Summary> getSummaries(LocalDate mealDate, String email) {
        Map<MenuCategory, MealRatingRepository.Summary> summaries = mealRatingRepository.summarize(mealDate)
                .stream()
                .collect(Collectors.toMap(MealRatingRepository.Summary::getMenuCategory, Function.identity()));

        Map<MenuCategory, Integer> myScores = email == null
                ? Collections.emptyMap()
                : mealRatingRepository.findByMemberAndMealDate(memberQueryService.getMemberByEmail(email), mealDate)
                        .stream()
                        .collect(Collectors.toMap(MealRating::getMenuCategory, MealRating::getScore));

        return Arrays.stream(MenuCategory.values())
                .map(category -> {
                    MealRatingRepository.Summary summary = summaries.get(category);
                    return MealRatingDTO.Response.Summary.builder()
                            .menuCategory(category)
                            .average(summary == null ? 0 : Math.round(summary.getAverage() * 10) / 10.0)
                            .count(summary == null ? 0 : summary.getCount())
                            .myScore(myScores.get(category))
                            .build();
                })
                .toList();
    }

    @Transactional
    public List<MealRatingDTO.Response.Summary> rate(String email, MealRatingDTO.Request.Rate request) {
        LocalDate today = today();
        MenuCategory category = request.getMenuCategory();

        if (!weeklyMenuRepository.existsByDateStartingWithAndMenuCategory(today.format(MENU_DATE_PREFIX), category)) {
            throw new WeeklyMenuException(ErrorCode.MEAL_RATING_MENU_NOT_FOUND);
        }

        Member member = memberQueryService.getMemberByEmail(email);
        mealRatingRepository.findByMemberAndMealDateAndMenuCategory(member, today, category)
                .ifPresentOrElse(
                        rating -> rating.changeScore(request.getScore()),
                        () -> mealRatingRepository.save(MealRating.create(member, today, category, request.getScore()))
                );

        return getSummaries(today, email);
    }
}
