package nova.mjs.domain.thingo.review.entity;

import lombok.Getter;

import static nova.mjs.domain.thingo.review.entity.ReviewKeywordGroup.*;

/**
 * 리뷰 키워드 태그(고정 enum). 화면설계 05-2-5 기준 3그룹 × 6개 + 특수값 1개.
 *
 * 각 값은 소속 그룹/이모지/한국어 라벨/F&B 전용 여부를 가진다.
 * - fbOnly = true 인 키워드(FOOD_PRICE 6개 + ADULT_MEAL)는
 *   명지도 카테고리 그룹이 'food'(식사 F&B)인 장소에서만 선택 가능하다.
 * - NONE_APPROPRIATE('적절한 키워드 없음')는 단독으로만 선택 가능하며,
 *   화면 표시 단계에서는 태그를 렌더하지 않는다.
 *
 * 이모지는 화면설계 기준이며, 프론트가 자체 매핑을 쓸 수 있으므로 참고용이다.
 */
@Getter
public enum ReviewKeyword {

    // 음식/가격 (F&B 전용)
    TASTY(FOOD_PRICE, "🤤", "맛있음", true),
    REVISIT(FOOD_PRICE, "🍚", "또갈집", true),
    VALUE(FOOD_PRICE, "🐷", "가성비", true),
    GENEROUS(FOOD_PRICE, "🥩", "양 혜자", true),
    FRESH(FOOD_PRICE, "🥬", "재료 신선", true),
    NOT_BAD(FOOD_PRICE, "🐣", "낫배드", true),

    // 분위기 (공통)
    CLEAN_LOOK(MOOD, "✨", "깔끔함", false),
    COZY(MOOD, "🚙", "아늑함", false),
    GOOD_VIBE(MOOD, "🎧", "느좋", false),
    LUXURIOUS(MOOD, "🔥", "고급짐", false),
    FOCUS(MOOD, "📖", "집중 굿", false),
    SOLO_DINING(MOOD, "👤", "혼밥 굿", false),

    // 기타 (공통, ADULT_MEAL만 F&B 전용)
    KIND(ETC, "🥰", "친절함", false),
    HYGIENIC(ETC, "🧼", "청결함", false),
    CLEAN_RESTROOM(ETC, "🚻", "화장실 깨끗", false),
    GROUP_OK(ETC, "👥", "단체 가능", false),
    ADULT_MEAL(ETC, "🙇🏻", "어른 식사 대접", true),
    NONE_APPROPRIATE(ETC, "", "적절한 키워드 없음", false);

    private final ReviewKeywordGroup group;
    private final String emoji;
    private final String label;
    private final boolean fbOnly;

    ReviewKeyword(ReviewKeywordGroup group, String emoji, String label, boolean fbOnly) {
        this.group = group;
        this.emoji = emoji;
        this.label = label;
        this.fbOnly = fbOnly;
    }

    /** '적절한 키워드 없음' 특수값인지 여부 (단독 선택 강제·표시 제외 판정에 사용) */
    public boolean isNoneAppropriate() {
        return this == NONE_APPROPRIATE;
    }
}
