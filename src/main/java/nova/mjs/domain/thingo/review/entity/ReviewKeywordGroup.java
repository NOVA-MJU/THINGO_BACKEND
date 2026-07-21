package nova.mjs.domain.thingo.review.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 리뷰 키워드 대분류 그룹.
 *
 * 리뷰 작성 화면(05-2-5)에서 키워드를 묶는 3개 섹션이다.
 * - FOOD_PRICE(음식/가격): F&B 장소에서만 노출되는 그룹
 * - MOOD(분위기): 모든 장소 공통
 * - ETC(기타): 모든 장소 공통. 단, '어른 식사 대접'만 F&B 전용
 */
@Getter
@RequiredArgsConstructor
public enum ReviewKeywordGroup {

    FOOD_PRICE("음식/가격"),
    MOOD("분위기"),
    ETC("기타");

    private final String label;
}
