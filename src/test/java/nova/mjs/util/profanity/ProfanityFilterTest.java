package nova.mjs.util.profanity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비속어 마스킹 단위 테스트.
 * 사전 매칭 + 문자 사이 구분자(공백/기호) 우회 탐지 + 길이 보존 마스킹을 검증한다.
 */
class ProfanityFilterTest {

    // 테스트용으로 사전 단어를 직접 주입(패키지 전용 생성자)
    private final ProfanityFilter filter = new ProfanityFilter(List.of("시발", "병신"));

    @Nested
    @DisplayName("마스킹")
    class Mask {

        @Test
        @DisplayName("사전에 있는 비속어를 * 로 치환한다")
        void should_maskProfanity_when_사전단어포함() {
            // given - when - then
            assertThat(filter.mask("병신아 진짜")).isEqualTo("**아 진짜");
        }

        @Test
        @DisplayName("비속어가 없으면 원문을 그대로 반환한다")
        void should_keepText_when_비속어없음() {
            // given - when - then
            assertThat(filter.mask("안녕하세요 반갑습니다")).isEqualTo("안녕하세요 반갑습니다");
        }

        @Test
        @DisplayName("길이를 보존하여 매칭 구간만 * 로 바꾼다")
        void should_preserveLength_when_마스킹() {
            // given - when - then
            assertThat(filter.mask("시발 놈아")).isEqualTo("** 놈아");
        }

        @Test
        @DisplayName("문자 사이 공백/기호 우회도 탐지해 마스킹한다")
        void should_maskEvasion_when_구분자삽입() {
            // given - when - then
            assertThat(filter.mask("시 발")).doesNotContain("발");
            assertThat(filter.mask("시.발 놈")).doesNotContain("시.발");
        }

        @Test
        @DisplayName("정상 단어 사이에 우연히 낀 음절은 오탐하지 않는다")
        void should_notMatch_when_사이에일반음절() {
            // given - when - then (시원한 발: 사이에 '원한'이 있어 매칭되지 않음)
            assertThat(filter.mask("시원한 발라드")).isEqualTo("시원한 발라드");
        }

        @Test
        @DisplayName("null 은 null, 빈 문자열은 빈 문자열을 반환한다")
        void should_handleNullAndBlank() {
            // given - when - then
            assertThat(filter.mask(null)).isNull();
            assertThat(filter.mask("")).isEmpty();
        }
    }
}
