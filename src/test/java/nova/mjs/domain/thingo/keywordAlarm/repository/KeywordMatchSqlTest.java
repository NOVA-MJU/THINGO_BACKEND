package nova.mjs.domain.thingo.keywordAlarm.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매칭 SQL 문자열 자체를 검증한다.
 *
 * SQL 이 String.format 으로 조립되고 정규식 이스케이프(\\1, %%, {0,3})가 섞여 있어서,
 * 컨테이너 없이도 "조립 결과가 의도한 문자열인지"는 확인해 둔다.
 */
class KeywordMatchSqlTest {

    private String matchSql() throws Exception {
        Field field = KeywordSubscriptionQueryRepositoryImpl.class.getDeclaredField("MATCH_SQL");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    @Test
    @DisplayName("조립된 SQL 의 이스케이프가 깨지지 않는다")
    void should_build_match_sql() throws Exception {
        String sql = matchSql();

        // LIKE 와일드카드는 %% -> % 로 풀려야 한다
        assertThat(sql).contains("LIKE '%' ||").doesNotContain("'%%'");

        // 중위 삽입 정규식: 역참조 \1 과 수량자 {0,3} 이 그대로 남아야 한다
        assertThat(sql).contains("regexp_replace(regexp_replace(lower(ks.keyword)");
        assertThat(sql).contains("'(..)(?=.)'");
        assertThat(sql).contains("\\1[가-힣]{0,3}");
        assertThat(sql).contains("'(^|[^가-힣])'");

        // 세 갈래(토큰 tsquery / 제목 부분일치 / 중위 삽입)가 모두 있어야 한다
        assertThat(sql).contains("to_tsquery('simple'");
        assertThat(sql).contains("char_length(regexp_replace(lower(ks.keyword)");

        // 길이 가드
        assertThat(sql).contains(">= 3").contains(">= 4");
    }
}
