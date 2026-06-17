package nova.mjs.domain.thingo.search.mapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PgUnifiedSearchMapperTest {

    private final PgUnifiedSearchMapper mapper = new PgUnifiedSearchMapper();

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
}
