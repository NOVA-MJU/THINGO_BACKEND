package nova.mjs.domain.thingo.search.repository;

import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL FTS + pg_trgm 통합 검색 native 쿼리 통합 테스트.
 */
@Testcontainers
@DataJpaTest(excludeAutoConfiguration = {
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
@EnableAutoConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(UnifiedSearchIndexQueryRepositoryImpl.class)
class UnifiedSearchIndexQueryRepositoryImplIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withInitScript("sql/unified_search_index.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database", () -> "postgresql");
        // @DataJpaTest 와 @EnableAutoConfiguration 이 JPA 리포지토리 빈을 중복 등록하는
        // 문제(BeanDefinitionOverrideException) 회피: 테스트 한정 override 허용.
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    // JPA 엔티티 리스너들이 의존하는 SearchIndexPublisher 를 슬라이스 컨텍스트에 제공한다.
    // (검색 쿼리 테스트라 실제 발행은 불필요 → mock 으로 컨텍스트 로드만 충족)
    @MockBean
    SearchIndexPublisher searchIndexPublisher;

    @Autowired
    UnifiedSearchIndexRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("keyword 비어있으면 latest 정렬로 전체 반환")
    void search_empty_keyword_returns_latest() {
        // given
        Instant base = Instant.now();
        repository.save(row("NOTICE", "공지 A", "내용", base.minus(2, ChronoUnit.DAYS)));
        repository.save(row("NOTICE", "공지 B", "내용", base.minus(1, ChronoUnit.DAYS)));
        repository.save(row("NEWS", "뉴스 C", "내용", base));
        repository.flush();

        // when
        Page<SearchResultRow> page = repository.search(
                "", null, null, "latest", null, 0.0d, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().get(0).title()).isEqualTo("뉴스 C");
    }

    @Test
    @DisplayName("title 매칭은 FTS로 검색된다")
    void search_keyword_matches_title_via_fts() {
        // given
        repository.save(rowWithTokens("NOTICE", "장학금 신청 안내", "총학생회 장학 안내", "장학금 장학", Instant.now()));
        repository.save(rowWithTokens("NEWS", "축제 일정", "본관 앞 축제 진행", "축제 일정", Instant.now()));
        repository.flush();

        // when
        Page<SearchResultRow> page = repository.search(
                "장학금", null, null, "relevance", null, 0.0d, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).title()).contains("장학금");
        assertThat(page.getContent().get(0).highlightedTitle()).contains("<em>");
    }

    @Test
    @DisplayName("type 필터가 동작한다")
    void search_filters_by_type() {
        // given
        repository.save(rowWithTokens("NOTICE", "장학금 안내", "내용", "장학금", Instant.now()));
        repository.save(rowWithTokens("NEWS", "장학금 기사", "내용", "장학금", Instant.now()));
        repository.flush();

        // when
        Page<SearchResultRow> page = repository.search(
                "장학금", "NEWS", null, "relevance", null, 0.0d, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).type()).isEqualTo("NEWS");
    }

    @Test
    @DisplayName("trigram 유사도로 오타에도 어느 정도 매칭")
    void search_trigram_handles_typo() {
        // given
        repository.save(rowWithTokens("NOTICE", "장학금 신청", "내용", "장학금 장학 신청", Instant.now()));
        repository.flush();

        // when - 오타 "장학금금"
        Page<SearchResultRow> page = repository.search(
                "장학금금", null, null, "relevance", null, 0.0d, PageRequest.of(0, 10));

        // then
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("suggest는 prefix 매칭 결과를 반환")
    void suggest_returns_prefix_matches() {
        // given
        repository.save(rowWithTokens("NOTICE", "장학금 안내", "x", "장학금", Instant.now()));
        repository.save(rowWithTokens("NOTICE", "장학사업 변경", "x", "장학", Instant.now()));
        repository.save(rowWithTokens("NEWS", "축제", "x", "축제", Instant.now()));
        repository.flush();

        // when
        var result = repository.suggest("장학", 10);

        // then
        assertThat(result).anyMatch(s -> s.contains("장학"));
        assertThat(result).noneMatch(s -> s.contains("축제"));
    }

    @Test
    @DisplayName("만료된 문서는 같은 키워드의 유효한 문서보다 후순위")
    void expired_ranks_below_valid() {
        // given - 동일 키워드, 하나는 마감 지남 / 하나는 아직 유효
        Instant now = Instant.now();
        repository.save(build("NOTICE", "scholarship", "장학금 신청 안내 (마감)", "내용",
                "장학금 장학 신청", now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)));
        repository.save(build("NOTICE", "scholarship", "장학금 신청 안내 (진행중)", "내용",
                "장학금 장학 신청", now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS)));
        repository.flush();

        // when
        Page<SearchResultRow> page = repository.search(
                "장학금", null, null, "relevance", null, 0.0d, PageRequest.of(0, 10));

        // then - 유효한(진행중) 문서가 위, 만료된 문서가 아래
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).title()).contains("진행중");
        assertThat(page.getContent().get(1).title()).contains("마감");
        assertThat(page.getContent().get(0).score())
                .isGreaterThan(page.getContent().get(1).score());
    }

    @Test
    @DisplayName("학칙(rule, 무기한)은 만료된 일반 공지보다 상위 노출")
    void evergreen_rule_is_surfaced() {
        // given - 무기한 학칙(valid_until null) + 마감 지난 일반 공지
        Instant now = Instant.now();
        repository.save(build("NOTICE", "rule", "출결 규정", "결석 처리 기준 안내",
                "출결 규정 결석", now.minus(400, ChronoUnit.DAYS), null));
        repository.save(build("NOTICE", "general", "출결 행사 안내", "내용",
                "출결 행사", now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)));
        repository.flush();

        // when
        Page<SearchResultRow> page = repository.search(
                "출결", null, null, "relevance", null, 0.0d, PageRequest.of(0, 10));

        // then - 학칙이 결과에 있고, 만료된 일반 공지보다 위
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent().get(0).title()).isEqualTo("출결 규정");
    }

    private UnifiedSearchIndex row(String type, String title, String content, Instant date) {
        return rowWithTokens(type, title, content, title + " " + content, date);
    }

    private UnifiedSearchIndex rowWithTokens(String type,
                                             String title,
                                             String content,
                                             String tokens,
                                             Instant date) {
        return build(type, null, title, content, tokens, date, null);
    }

    private UnifiedSearchIndex build(String type,
                                     String category,
                                     String title,
                                     String content,
                                     String tokens,
                                     Instant date,
                                     Instant validUntil) {
        String originalId = UUID.randomUUID().toString();
        return UnifiedSearchIndex.of(
                type + ":" + originalId,
                originalId,
                type,
                category,
                title,
                content,
                null, null, null,
                0, 0, 0.0d,
                date,
                validUntil,
                tokens
        );
    }
}
