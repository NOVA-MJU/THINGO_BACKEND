package nova.mjs.domain.thingo.dday;

import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import nova.mjs.domain.thingo.calendar.dto.MjuCalendarDTO;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import nova.mjs.domain.thingo.calendar.service.MjuCalendarService;
import nova.mjs.domain.thingo.dday.dto.DDayDto;
import nova.mjs.domain.thingo.dday.service.DDayService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 디데이 조회 통합 테스트.
 * 학사일정(MjuCalendar) 기준 임박순 상위 4건 반환, phase/ddayValue 계산, 말줄임 검증.
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
@Import({DDayService.class, MjuCalendarService.class, DDayServiceIT.TestBeans.class})
class DDayServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withInitScript("sql/mju_calendar.sql");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database", () -> "postgresql");
    }

    // MjuCalendarService 생성자에 필요한 RestTemplate 더미 빈
    @TestConfiguration
    static class TestBeans {
        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }
    }

    // 엔티티 리스너(ES 색인 발행) 무력화
    @MockBean
    SearchIndexPublisher searchIndexPublisher;

    @Autowired
    DDayService ddayService;

    @Autowired
    MjuCalendarRepository calendarRepository;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void clean() {
        calendarRepository.deleteAll();
    }

    private void saveCalendar(LocalDate start, LocalDate end, String description) {
        calendarRepository.saveAndFlush(MjuCalendar.create(
                new MjuCalendarDTO(today.getYear(), start, end, description)));
    }

    @Test
    @DisplayName("종료된 일정(endDate < today)은 디데이에서 제외된다")
    void should_excludeEndedEvents() {
        // given
        saveCalendar(today.minusDays(10), today.minusDays(1), "이미 끝난 일정"); // 제외 대상
        saveCalendar(today.plusDays(1), today.plusDays(2), "다가오는 일정");      // 포함 대상

        // when
        List<DDayDto.Response> result = ddayService.getDDays();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventName()).isEqualTo("다가오는 일정");
    }

    @Test
    @DisplayName("시작 전 일정은 UPCOMING, 시작일까지 카운트다운한다")
    void should_countdownToStart_when_upcoming() {
        // given
        saveCalendar(today.plusDays(3), today.plusDays(5), "축제");

        // when
        DDayDto.Response result = ddayService.getDDays().get(0);

        // then
        assertThat(result.getPhase()).isEqualTo(DDayDto.Phase.UPCOMING);
        assertThat(result.getTargetDate()).isEqualTo(today.plusDays(3));
        assertThat(result.getDdayValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("진행 중 일정은 ONGOING, 종료일까지 카운트다운한다")
    void should_countdownToEnd_when_ongoing() {
        // given
        saveCalendar(today.minusDays(1), today.plusDays(2), "수강신청기간");

        // when
        DDayDto.Response result = ddayService.getDDays().get(0);

        // then
        assertThat(result.getPhase()).isEqualTo(DDayDto.Phase.ONGOING);
        assertThat(result.getTargetDate()).isEqualTo(today.plusDays(2));
        assertThat(result.getDdayValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("임박순으로 정렬되어 최대 4건만 반환한다")
    void should_returnTop4_sortedByImminence() {
        // given - 5건 모두 유효(미래)
        saveCalendar(today.plusDays(5), today.plusDays(6), "5일후");
        saveCalendar(today.plusDays(1), today.plusDays(2), "1일후");
        saveCalendar(today.plusDays(4), today.plusDays(5), "4일후");
        saveCalendar(today.plusDays(2), today.plusDays(3), "2일후");
        saveCalendar(today.plusDays(3), today.plusDays(4), "3일후");

        // when
        List<DDayDto.Response> result = ddayService.getDDays();

        // then
        assertThat(result).hasSize(4);
        assertThat(result).extracting(DDayDto.Response::getEventName)
                .containsExactly("1일후", "2일후", "3일후", "4일후");
    }

    @Test
    @DisplayName("이벤트명이 12자 초과면 말줄임표로 자른다")
    void should_truncateName_when_over12Chars() {
        // given - 13자
        String longName = "가나다라마바사아자차카타파"; // 13자
        saveCalendar(today.plusDays(1), today.plusDays(2), longName);

        // when
        DDayDto.Response result = ddayService.getDDays().get(0);

        // then
        assertThat(result.getEventName()).isEqualTo(longName);
        assertThat(result.getEventNameTruncated()).hasSize(12);
        assertThat(result.getEventNameTruncated()).endsWith("…");
    }

    @Test
    @DisplayName("이벤트명이 12자 이하면 그대로 둔다")
    void should_keepName_when_within12Chars() {
        // given - 12자
        String name = "가나다라마바사아자차카타"; // 12자
        saveCalendar(today.plusDays(1), today.plusDays(2), name);

        // when
        DDayDto.Response result = ddayService.getDDays().get(0);

        // then
        assertThat(result.getEventNameTruncated()).isEqualTo(name);
    }
}
