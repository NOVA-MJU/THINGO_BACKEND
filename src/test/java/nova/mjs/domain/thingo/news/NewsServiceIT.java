package nova.mjs.domain.thingo.news;

import nova.mjs.domain.thingo.ElasticSearch.EntityListner.NewsEntityListener;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.news.DTO.NewsResponseDTO;
import nova.mjs.domain.thingo.news.entity.News;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import nova.mjs.domain.thingo.news.service.NewsCrawlerService;
import nova.mjs.domain.thingo.news.service.NewsService;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 뉴스 크롤링 통합 테스트 (실제 news.mju.ac.kr + Testcontainers PostgreSQL).
 * 세션 페이지네이션 -> 상세 섹션 판별(보도/사회) -> 저장 -> 중복 제거 전 과정을 실DB로 검증.
 */
@Disabled("실서비스(news.mju.ac.kr) 라이브 크롤링 + Docker 의존. CI 자동 실행 제외, 수동 검증용.")
@Testcontainers
@DataJpaTest(properties = "spring.main.allow-bean-definition-overriding=true", excludeAutoConfiguration = {
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
@Import({NewsService.class, NewsCrawlerService.class, NewsEntityListener.class})
class NewsServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create"); // 엔티티로 news 테이블 생성
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database", () -> "postgresql");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    // 엔티티 리스너의 ES 색인 발행 무력화
    @MockBean
    SearchIndexPublisher searchIndexPublisher;

    @Autowired
    NewsService newsService;

    @Autowired
    NewsRepository newsRepository;

    @Test
    @DisplayName("증분 크롤링 - 보도/사회만 저장되고 중복은 재저장되지 않는다")
    void should_saveOnlyReportAndSociety_andDedupe() {
        // when - 빈 DB에서 첫 증분 크롤링 (여러 페이지 세션 유지 walk)
        List<NewsResponseDTO> firstRun = newsService.crawlLatest();

        // then - 신규 저장됨, 전부 보도/사회, newsIndex 중복 없음
        long total = newsRepository.count();
        System.out.println("[검증] 1차 저장 건수 = " + total);
        assertThat(total).isGreaterThan(0);

        List<News> all = newsRepository.findAll();
        assertThat(all).allSatisfy(n ->
                assertThat(n.getCategory()).isIn(News.Category.REPORT, News.Category.SOCIETY));
        assertThat(all.stream().map(News::getNewsIndex).distinct().count()).isEqualTo(total);

        long reportCount = all.stream().filter(n -> n.getCategory() == News.Category.REPORT).count();
        long societyCount = all.stream().filter(n -> n.getCategory() == News.Category.SOCIETY).count();
        System.out.printf("[검증] REPORT=%d, SOCIETY=%d%n", reportCount, societyCount);
        System.out.println("[검증] 1차 반환(저장)건수 = " + firstRun.size());

        // when - 같은 상태에서 재실행
        List<NewsResponseDTO> secondRun = newsService.crawlLatest();

        // then - 중복 제거로 신규 0건, DB 총건수 불변
        System.out.println("[검증] 2차 반환(저장)건수 = " + secondRun.size());
        assertThat(secondRun).isEmpty();
        assertThat(newsRepository.count()).isEqualTo(total);
    }
}
