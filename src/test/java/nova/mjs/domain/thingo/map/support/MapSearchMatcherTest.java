package nova.mjs.domain.thingo.map.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명지도 검색 매칭/스코어링 단위 테스트.
 * 정규화·초성·오타 허용·다중 시그널 점수의 우선순위를 검증한다.
 */
class MapSearchMatcherTest {

    private final MapSearchMatcher matcher = new MapSearchMatcher();

    @Nested
    @DisplayName("정규화")
    class Normalize {

        @Test
        @DisplayName("공백/기호를 제거하고 소문자로 통일한다")
        void should_stripSpacesAndSymbols_andLowercase() {
            // given - when - then
            assertThat(matcher.normalize("투썸 플레이스!")).isEqualTo("투썸플레이스");
            assertThat(matcher.normalize("GS25 명지대점")).isEqualTo("gs25명지대점");
        }
    }

    @Nested
    @DisplayName("초성 변환/판정")
    class Chosung {

        @Test
        @DisplayName("한글 음절의 초성 시퀀스를 뽑는다")
        void should_extractChosung() {
            // given - when - then
            assertThat(matcher.toChosung("투썸플레이스")).isEqualTo("ㅌㅆㅍㄹㅇㅅ");
            assertThat(matcher.toChosung("종합관")).isEqualTo("ㅈㅎㄱ");
        }

        @Test
        @DisplayName("초성 자모로만 이뤄진 입력만 초성 검색으로 판정한다")
        void should_detectChosungOnlyQuery() {
            // given - when - then
            assertThat(matcher.isChosungQuery("ㅌㅆ")).isTrue();
            assertThat(matcher.isChosungQuery("투썸")).isFalse();
            assertThat(matcher.isChosungQuery("ㅌ썸")).isFalse();
            assertThat(matcher.isChosungQuery("")).isFalse();
        }
    }

    @Nested
    @DisplayName("관련도 점수")
    class Score {

        @Test
        @DisplayName("정확 일치 > 접두 일치 > 부분 포함 순으로 높은 점수를 준다")
        void should_rankExactOverPrefixOverSubstring() {
            // given
            String name = "종합관";
            String category = "건물";

            // when
            double exact = matcher.score(name, category, "종합관");
            double prefix = matcher.score(name, category, "종합");
            double substring = matcher.score("투썸플레이스 명지대점", "카페", "명지대");

            // then
            assertThat(exact).isEqualTo(100.0);
            assertThat(prefix).isEqualTo(70.0);
            assertThat(substring).isEqualTo(45.0);
            assertThat(exact).isGreaterThan(prefix);
            assertThat(prefix).isGreaterThan(substring);
        }

        @Test
        @DisplayName("초성 입력은 이름 초성과 매칭된다")
        void should_matchByChosung() {
            // given - when
            double exactChosung = matcher.score("투썸", "카페", "ㅌㅆ");
            double prefixChosung = matcher.score("투썸플레이스", "카페", "ㅌㅆ");

            // then
            assertThat(exactChosung).isEqualTo(90.0);   // 이름 전체 초성과 정확 일치
            assertThat(prefixChosung).isEqualTo(65.0);  // 이름 초성의 접두 일치
        }

        @Test
        @DisplayName("3글자 이상 검색어는 오타가 있어도 편집거리 유사도로 매칭된다")
        void should_matchDespiteTypo() {
            // given - when
            double typo = matcher.score("스타벅스", "카페", "스타벅쓰");

            // then
            assertThat(typo).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("2글자 이하 검색어는 오타 허용을 적용하지 않는다 (동음이의어 오탐 방지)")
        void should_notApplyFuzzy_when_queryTooShort() {
            // given - '투썸'과 '투다'가 1글자만 달라 편집거리 유사도는 높지만, 2글자 검색어라 오타로 보지 않는다
            // when
            double typo = matcher.score("투다리", "주점", "투썸");

            // then
            assertThat(typo).isEqualTo(0.0);
        }

        @Test
        @DisplayName("단어 경계를 넘어선 우연한 일치는 매칭되지 않는다 (공백 제거 전 토큰 단위 비교)")
        void should_notMatch_acrossWordBoundary_byCoincidence() {
            // given - '투다리 하나로점'을 공백 제거해 통으로 비교하면 '투다'가 '투썸'과 오탐될 수 있다
            // when
            double result1 = matcher.score("투다리 하나로점", "주점", "투썸");
            double result2 = matcher.score("투데이하우스", "한식", "투썸");

            // then
            assertThat(result1).isEqualTo(0.0);
            assertThat(result2).isEqualTo(0.0);
        }

        @Test
        @DisplayName("이름이 안 맞아도 카테고리명이 맞으면 결과에 포함된다")
        void should_matchByCategoryLabel() {
            // given - when
            double byCategory = matcher.score("스타벅스", "카페", "카페");

            // then
            assertThat(byCategory).isEqualTo(25.0);
        }

        @Test
        @DisplayName("이름/카테고리 어디에도 안 맞으면 0점(결과 제외)")
        void should_returnZero_when_noMatch() {
            // given - when - then
            assertThat(matcher.score("스타벅스", "카페", "전혀없는검색어")).isEqualTo(0.0);
        }
    }
}
