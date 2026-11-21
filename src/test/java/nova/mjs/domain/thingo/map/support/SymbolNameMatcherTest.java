package nova.mjs.domain.thingo.map.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네이버 지도 기본 글자 이름과 우리 핀 이름 대조 기준 (앱 lib/maps/match-symbol.check.mjs와 같은 사례).
 */
class SymbolNameMatcherTest {

    @Test
    @DisplayName("지점 꼬리, 공백, 괄호가 달라도 같은 가게로 본다")
    void sameStoreWithDifferentNotation() {
        assertThat(SymbolNameMatcher.isSimilarName("스타벅스 명지대점", "스타벅스")).isTrue();
        assertThat(SymbolNameMatcher.isSimilarName("삼정식당", "삼정 식당")).isTrue();
        assertThat(SymbolNameMatcher.isSimilarName("제일쌀국수(본점)", "제일쌀국수")).isTrue();
    }

    @Test
    @DisplayName("한쪽 이름이 다른 쪽에 들어 있으면 같은 곳으로 본다")
    void containedName() {
        assertThat(SymbolNameMatcher.isSimilarName("명지대학교 인문캠퍼스 학생회관", "학생회관")).isTrue();
    }

    @Test
    @DisplayName("다른 가게는 같은 곳으로 보지 않는다")
    void differentStores() {
        assertThat(SymbolNameMatcher.isSimilarName("호랑마라", "탄뽀뽀")).isFalse();
        assertThat(SymbolNameMatcher.isSimilarName("GS25", "CU")).isFalse();
        assertThat(SymbolNameMatcher.isSimilarName("", "CU")).isFalse();
    }
}
