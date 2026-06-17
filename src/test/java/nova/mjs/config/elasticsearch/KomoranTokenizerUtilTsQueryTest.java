package nova.mjs.config.elasticsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * buildTsQuery 단위 테스트.
 *
 * 색인(search_tokens)이 Komoran 형태소 기반이므로, 검색어도 동일하게 토큰화해
 * to_tsquery 입력(OR 결합)으로 만들어야 복합어/다어절 recall 이 확보된다.
 */
class KomoranTokenizerUtilTsQueryTest {

    @Test
    @DisplayName("빈 입력은 빈 문자열")
    void should_returnEmpty_when_blankInput() {
        // given & when & then
        assertThat(KomoranTokenizerUtil.buildTsQuery("")).isEmpty();
        assertThat(KomoranTokenizerUtil.buildTsQuery(null)).isEmpty();
    }

    @Test
    @DisplayName("복합어는 개별 형태소로 분해되어 OR 결합된다")
    void should_decomposeCompound_when_singleToken() {
        // given
        String keyword = "성적우수장학금";

        // when
        String tsQuery = KomoranTokenizerUtil.buildTsQuery(keyword);

        // then - 장학금 형태소가 분해돼 포함되고, OR 연산자로 결합
        assertThat(tsQuery).contains("장학금");
        assertThat(tsQuery).contains("|");
    }

    @Test
    @DisplayName("띄어쓰기 변형은 compact 형(공백제거)을 포함해 띄어쓰기 무관 매칭")
    void should_includeCompactForm_when_spacedInput() {
        // given
        String keyword = "수강 신청";

        // when
        String tsQuery = KomoranTokenizerUtil.buildTsQuery(keyword);

        // then
        assertThat(tsQuery).contains("수강신청");
    }

    @Test
    @DisplayName("다어절 입력은 각 토큰이 OR 로 결합된다")
    void should_joinTokensWithOr_when_multiWord() {
        // given
        String keyword = "출결 규정";

        // when
        String tsQuery = KomoranTokenizerUtil.buildTsQuery(keyword);

        // then
        assertThat(tsQuery).contains("출결");
        assertThat(tsQuery).contains("규정");
        assertThat(tsQuery).contains("|");
    }

    @Test
    @DisplayName("명사 형태소가 없는 노이즈 입력은 빈 문자열")
    void should_returnEmpty_when_noContentMorpheme() {
        // given & when
        String tsQuery = KomoranTokenizerUtil.buildTsQuery("ㅁㅈㄷ");

        // then
        assertThat(tsQuery).isEmpty();
    }

    @Test
    @DisplayName("2글자 일반 명사(전과)는 compact 형으로 보존된다")
    void should_keepShortWord_when_subTwoCharMorpheme() {
        // given & when - Komoran 이 2글자 미만 형태소로 쪼개도 compact 형이 살아남아야 함
        String tsQuery = KomoranTokenizerUtil.buildTsQuery("전과");

        // then
        assertThat(tsQuery).contains("전과");
    }

    @Test
    @DisplayName("AND 쿼리는 내용 형태소를 & 로 결합한다 (coverage boost 용)")
    void should_joinMorphemesWithAnd_when_multiWord() {
        // given & when
        String and = KomoranTokenizerUtil.buildTsQueryAnd("기숙사 신청");

        // then
        assertThat(and).contains("기숙사");
        assertThat(and).contains("신청");
        assertThat(and).contains("&");
    }

    @Test
    @DisplayName("AND 쿼리도 노이즈 입력은 빈 문자열")
    void should_returnEmptyAnd_when_noContentMorpheme() {
        // given & when & then
        assertThat(KomoranTokenizerUtil.buildTsQueryAnd("ㅎㅇ")).isEmpty();
    }
}
