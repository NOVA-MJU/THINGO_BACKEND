package nova.mjs.domain.thingo.search.mapper;

import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.search.indexing.DeadlineExtractor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PgUnifiedSearchMapperTest {

    private final PgUnifiedSearchMapper mapper = new PgUnifiedSearchMapper(new DeadlineExtractor());

    @Test
    void buildId_returns_type_colon_originalId() {
        // when
        String id = mapper.buildId("NOTICE", "abc-123");

        // then
        assertThat(id).isEqualTo("NOTICE:abc-123");
    }

    @Test
    void buildId_null_safe() {
        // when
        String id = mapper.buildId(null, null);

        // then
        assertThat(id).isEqualTo(":");
    }

    @Test
    void computePopularity_zero_when_no_engagement() {
        // when
        double score = mapper.computePopularity(0, 0, Instant.now());

        // then
        assertThat(score).isEqualTo(0.0d);
    }

    @Test
    void computePopularity_weights_comments_more_than_likes() {
        // given - 동일 시점
        Instant now = Instant.now();

        // when
        double likeOnly = mapper.computePopularity(10, 0, now);
        double commentOnly = mapper.computePopularity(0, 10, now);

        // then
        assertThat(commentOnly).isGreaterThan(likeOnly);
    }

    @Test
    void computePopularity_decays_with_age() {
        // given
        Instant fresh = Instant.now();
        Instant old = fresh.minus(Duration.ofDays(60));

        // when
        double freshScore = mapper.computePopularity(100, 50, fresh);
        double oldScore = mapper.computePopularity(100, 50, old);

        // then - 60일 = halflife 2회 → 약 1/4
        assertThat(oldScore).isLessThan(freshScore);
        assertThat(oldScore).isLessThan(freshScore * 0.5);
    }

    @Test
    void computePopularity_handles_null_date_without_throwing() {
        // when
        double score = mapper.computePopularity(5, 5, null);

        // then
        assertThat(score).isGreaterThan(0.0d);
    }

    @Test
    void resolveValidUntil_uses_explicit_endDate_when_present() {
        // given - 명시적 종료일을 가진 문서(학사일정류)
        Instant end = LocalDate.of(2026, 5, 1).atTime(23, 59, 59)
                .atZone(ZoneId.systemDefault()).toInstant();
        SearchDocument doc = stub("MJU_CALENDAR", end);

        // when
        Instant result = mapper.resolveValidUntil(doc, "본문 2026.01.01");

        // then - 본문 파싱이 아니라 명시적 종료일을 사용한다
        assertThat(result).isEqualTo(end);
    }

    @Test
    void resolveValidUntil_parses_notice_content_when_no_explicit() {
        // given - 공지, 종료일 없음 → 본문에서 추정
        SearchDocument doc = stub("NOTICE", null);

        // when
        Instant result = mapper.resolveValidUntil(doc, "신청 마감 2026.03.15 까지");

        // then
        LocalDate parsed = LocalDate.ofInstant(result, ZoneId.systemDefault());
        assertThat(parsed).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void resolveValidUntil_null_for_non_notice_without_explicit() {
        // given - 공지가 아니고 종료일도 없음
        SearchDocument doc = stub("NEWS", null);

        // when - 본문에 날짜가 있어도 NEWS 는 추정 대상이 아니다
        Instant result = mapper.resolveValidUntil(doc, "행사 2026.03.15 진행");

        // then
        assertThat(result).isNull();
    }

    private SearchDocument stub(String type, Instant validUntil) {
        return new SearchDocument() {
            @Override public String getId() { return "1"; }
            @Override public String getTitle() { return "title"; }
            @Override public String getContent() { return ""; }
            @Override public String getType() { return type; }
            @Override public Instant getInstant() { return Instant.now(); }
            @Override public Instant getValidUntil() { return validUntil; }
        };
    }
}
