package nova.mjs.domain.thingo.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * PostgreSQL 통합 검색 인덱스 부가 객체 초기화.
 *
 * JPA ddl-auto=update 가 생성하지 못하는 항목을 기동 시 멱등 적용한다.
 *  - pg_trgm 확장
 *  - GIN 인덱스 (search_vector, search_tokens, title)
 *  - search_vector 자동 갱신 트리거 함수
 *
 * 적용 시점: Spring 컨텍스트 로드 + ddl-auto 처리 이후. (ApplicationRunner)
 *
 * 운영 권한 요구: CREATE EXTENSION (RDS masteruser 가능),
 *                CREATE FUNCTION/TRIGGER/INDEX.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PgSearchSchemaInitializer {

    private static final String SCRIPT_PATH = "sql/postgres-search-init.sql";
    private static final String TABLE_NAME = "unified_search_index";

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner pgSearchSchemaRunner() {
        return args -> {
            if (!isPostgres()) {
                log.info("[PgSearch][Schema] non-PostgreSQL datasource detected. skip");
                return;
            }
            if (!tableExists()) {
                log.warn("[PgSearch][Schema] table '{}' not found. skip", TABLE_NAME);
                return;
            }

            String script = loadScript();
            int ok = 0;
            int failed = 0;
            for (String statement : splitStatements(script)) {
                String sql = statement.trim();
                if (sql.isEmpty()) continue;
                try {
                    jdbcTemplate.execute(sql);
                    ok++;
                } catch (Exception e) {
                    // 운영 RDS 에서 권한 부족으로 CREATE EXTENSION 등이 실패할 수 있다.
                    // 기동 자체는 막지 않고 경고만 남긴다.
                    // 사전에 DBA 가 수동 적용했다면 후속 statement 는 IF EXISTS / OR REPLACE 로 멱등 처리됨.
                    failed++;
                    log.warn("[PgSearch][Schema] statement skipped. sql={}, reason={}",
                            preview(sql), e.getMessage());
                }
            }
            log.info("[PgSearch][Schema] init done. ok={}, skipped={}", ok, failed);
        };
    }

    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((java.sql.Connection c) ->
                    c.getMetaData().getDatabaseProductName());
            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception e) {
            log.warn("[PgSearch][Schema] db product probe failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean tableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + " WHERE table_name = ? ",
                Integer.class,
                TABLE_NAME);
        return count != null && count > 0;
    }

    private String loadScript() {
        try (var in = new ClassPathResource(SCRIPT_PATH).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PG search init script load failed: " + SCRIPT_PATH, e);
        }
    }

    /**
     * 세미콜론 split.
     * - PL/pgSQL 본문 ($$ ... $$) 안의 세미콜론은 보존한다.
     */
    private static java.util.List<String> splitStatements(String script) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inDollar = false;
        int i = 0;
        while (i < script.length()) {
            if (i + 1 < script.length()
                    && script.charAt(i) == '$' && script.charAt(i + 1) == '$') {
                inDollar = !inDollar;
                buf.append("$$");
                i += 2;
                continue;
            }
            char c = script.charAt(i);
            if (c == ';' && !inDollar) {
                result.add(buf.toString());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
            i++;
        }
        if (buf.length() > 0) {
            result.add(buf.toString());
        }
        return result;
    }

    private static String preview(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "..." : oneLine;
    }
}
