package nova.mjs.domain.thingo.search.indexing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineExtractorTest {

    private final DeadlineExtractor extractor = new DeadlineExtractor();

    private LocalDate dateOf(Optional<Instant> result) {
        return result.map(i -> LocalDate.ofInstant(i, ZoneId.systemDefault())).orElse(null);
    }

    @Test
    void should_단일_마감일_추출_when_점구분형식() {
        // given - when
        Optional<Instant> result = extractor.extract("신청 마감: 2026.03.15 까지");

        // then
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void should_끝날짜_채택_when_기간표기() {
        // given - 시작~종료 범위
        Optional<Instant> result = extractor.extract("신청기간 2026.03.01 ~ 2026.03.15");

        // then - 종료일(늦은 날짜)
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void should_추출_when_한글_년월일() {
        // given - when
        Optional<Instant> result = extractor.extract("2026년 3월 15일까지 접수합니다");

        // then
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void should_추출_when_하이픈형식() {
        // given - when
        Optional<Instant> result = extractor.extract("~ 2026-03-05 제출");

        // then
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void should_가장_늦은_날짜_when_여러날짜() {
        // given - 시행일과 마감일이 함께 등장
        Optional<Instant> result = extractor.extract("공고 2026.01.01 시행, 접수 2026.02.28 까지");

        // then
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void should_empty_when_날짜없음() {
        // given - when - then
        assertThat(extractor.extract("장학금 신청 안내입니다")).isEmpty();
    }

    @Test
    void should_empty_when_학년도표기() {
        // given - 월/일 없는 연도 표기는 마감이 아니다
        assertThat(extractor.extract("2025학년도 1학기 장학금")).isEmpty();
    }

    @Test
    void should_empty_when_null_또는_blank() {
        // given - when - then
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void should_그날_끝시각_when_추출() {
        // given - when
        Instant instant = extractor.extract("2026.03.15").orElseThrow();

        // then - 23:59:59 (그날까지 유효)
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        assertThat(ldt.getHour()).isEqualTo(23);
        assertThat(ldt.getMinute()).isEqualTo(59);
    }

    @Test
    void should_끝날짜_채택_when_기간끝에_연도생략() {
        // given - 학점포기 신청 공지 형식: 끝 날짜에 연도가 없다
        Optional<Instant> result = extractor.extract("2. 신청기간 : 2026. 10. 12.(월)~10. 16.(금) 17:00 3. 신청방법");

        // then - 시작일(10.12)이 아니라 끝 날짜(10.16)
        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void should_같은달_끝날짜_when_월까지_생략() {
        Optional<Instant> result = extractor.extract("접수 2026년 3월 2일(월) ~ 6일(금)");

        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 6));
    }

    @Test
    void should_다음해_끝날짜_when_해를_넘기는_기간() {
        Optional<Instant> result = extractor.extract("동계 계절학기 2026.12.21 ~ 01.13");

        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2027, 1, 13));
    }

    @Test
    void should_연도있는_끝날짜는_그대로_when_기간에_연도표기() {
        Optional<Instant> result = extractor.extract("신청기간 2026.03.01(일) ~ 2026.03.15(일)");

        assertThat(dateOf(result)).isEqualTo(LocalDate.of(2026, 3, 15));
    }
}
