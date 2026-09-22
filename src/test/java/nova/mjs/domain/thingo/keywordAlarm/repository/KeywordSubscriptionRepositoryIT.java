package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.member.entity.Member;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 키워드 구독 리포지토리 통합 테스트.
 * - (member, keyword) 유일 제약
 * - @ElementCollection 카테고리 round-trip
 * - 소유권 조회(findByIdAndMember)
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
class KeywordSubscriptionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private KeywordSubscriptionRepository repository;

    @Autowired
    private TestEntityManager em;

    /** JPA 엔티티 리스너(StudentCouncilNoticeEntityListener 등)가 요구하는 의존성 충족용 */
    @MockBean
    private SearchIndexPublisher searchIndexPublisher;

    private Member 영속회원(String email) {
        Member member = Member.builder()
                .uuid(UUID.randomUUID())
                .role(Member.Role.USER)
                .name("테스터")
                .email(email)
                .password("encoded")
                .college(College.AI_SOFTWARE)
                .build();
        return em.persistAndFlush(member);
    }

    @Test
    @DisplayName("카테고리 집합이 그대로 저장되고 조회된다")
    void should_round_trip_카테고리() {
        // given
        Member member = 영속회원("a@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "장학",
                Set.of(AlarmCategory.NOTICE, AlarmCategory.MJU_CALENDAR)));
        em.clear();

        // when
        List<KeywordSubscription> result = repository.findByMemberOrderByIdDesc(member);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategories())
                .containsExactlyInAnyOrder(AlarmCategory.NOTICE, AlarmCategory.MJU_CALENDAR);
    }

    @Test
    @DisplayName("같은 회원이 같은 키워드를 중복 저장하면 제약 위반")
    void should_violate_unique_when_중복키워드() {
        // given
        Member member = 영속회원("b@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "장학", Set.of(AlarmCategory.NOTICE)));

        // when & then
        assertThatThrownBy(() ->
                repository.saveAndFlush(KeywordSubscription.of(member, "장학", Set.of(AlarmCategory.COMMUNITY))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByMemberAndKeyword 로 중복 여부를 판별한다")
    void should_detect_중복_existsBy() {
        // given
        Member member = 영속회원("c@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "수강신청", Set.of(AlarmCategory.MJU_CALENDAR)));

        // when & then
        assertThat(repository.existsByMemberAndKeyword(member, "수강신청")).isTrue();
        assertThat(repository.existsByMemberAndKeyword(member, "기말고사")).isFalse();
    }

    @Test
    @DisplayName("접두 매칭: '장학' 구독이 '장학금' 포함 문서와 매칭된다")
    void should_prefix_match() {
        // given
        Member member = 영속회원("prefix@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "장학", Set.of(AlarmCategory.NOTICE)));
        String docTokens = KomoranTokenizerUtil.buildSearchTokens("2026 교내 장학금 신청 안내", null, "장학금 신청 안내");

        // when
        List<KeywordMatch> matches = repository.findMatchingSubscriptions(
                "NOTICE", docTokens, "2026 교내 장학금 신청 안내");

        // then
        assertThat(matches).extracting(KeywordMatch::keyword).contains("장학");
    }

    @Test
    @DisplayName("복합어 보조 매칭: 형태소가 쪼갠 '수강신청'을 원문 제목으로 잡는다")
    void should_match_compound_keyword_by_title() {
        // given - 형태소 분석이 제목을 '수강'/'신청' 으로 쪼개 접두 tsquery 가 '수강신청' 을 놓치는 상황
        Member member = 영속회원("compound@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "수강신청", Set.of(AlarmCategory.NOTICE)));
        String title = "2025학년도 동계 계절수업 안내(수강신청 및 등록)";
        String docTokens = "2025 학년 동계 계절 수업 안내 수강 신청 등록";

        // when & then
        assertThat(repository.findMatchingSubscriptions("NOTICE", docTokens, title))
                .extracting(KeywordMatch::keyword)
                .contains("수강신청");
    }

    @Test
    @DisplayName("중위 삽입 매칭: '해외탐방' 구독이 '해외문화탐방' 공지를 잡는다")
    void should_match_keyword_with_infix() {
        // given
        Member member = 영속회원("infix@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "해외탐방", Set.of(AlarmCategory.NOTICE)));
        String title = "2026학년도 동계 AI·전공융합 해외문화탐방 참가자 선발 안내";
        String docTokens = "2026 학년 동계 ai 전공 융합 해외 문화 탐방 참가자 선발 안내";

        // when & then
        assertThat(repository.findMatchingSubscriptions("NOTICE", docTokens, title))
                .extracting(KeywordMatch::keyword)
                .contains("해외탐방");
    }

    @Test
    @DisplayName("중위 삽입 매칭은 단어 경계를 지킨다('수강신청' != '재수강 ... 신청원서')")
    void should_respect_word_boundary_in_infix_match() {
        // given
        Member member = 영속회원("boundary@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "수강신청", Set.of(AlarmCategory.NOTICE)));
        String title = "2026학년도 1학기 재수강 처리 신청원서 제출 안내";
        String docTokens = "2026 학년 1 학기 재 수강 처리 신청 원서 제출 안내";

        // when & then
        assertThat(repository.findMatchingSubscriptions("NOTICE", docTokens, title)).isEmpty();
    }

    @Test
    @DisplayName("2글자 키워드는 제목 부분일치로 확장하지 않는다('공지' != '인공지능')")
    void should_not_substring_match_short_keyword() {
        // given
        Member member = 영속회원("short@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "공지", Set.of(AlarmCategory.NOTICE)));
        String title = "[인공지능·소프트웨어융합대학 교학팀] 계약직원 채용";
        String docTokens = "인공 지능 소프트웨어 융합 대학 교학팀 계약 직원 채용";

        // when & then
        assertThat(repository.findMatchingSubscriptions("NOTICE", docTokens, title)).isEmpty();
    }

    @Test
    @DisplayName("표준 Topic 구독은 Topic ID로만 매칭하고 자유 키워드 쿼리와 중복되지 않는다")
    void should_match_topic_subscription_only_by_topic_id() {
        Member member = 영속회원("topic@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(
                member, "졸업", "GRADUATION", Set.of(AlarmCategory.NOTICE)));
        String docTokens = KomoranTokenizerUtil.buildSearchTokens("졸업 안내", null, "");

        assertThat(repository.findMatchingSubscriptions("NOTICE", docTokens, "졸업 안내")).isEmpty();
        assertThat(repository.findMatchingTopicSubscriptions(
                AlarmCategory.NOTICE, Set.of("GRADUATION", "GRADUATION_DEFERRAL")))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.keyword()).isEqualTo("졸업");
                    assertThat(match.topicId()).isEqualTo("GRADUATION");
                });
    }

    @Test
    @DisplayName("카테고리 게이트: NOTICE 구독은 COMMUNITY 문서와 매칭되지 않는다")
    void should_not_match_other_category() {
        // given
        Member member = 영속회원("gate@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(member, "장학", Set.of(AlarmCategory.NOTICE)));
        String docTokens = KomoranTokenizerUtil.buildSearchTokens("장학금 안내", null, "장학금");

        // when & then
        assertThat(repository.findMatchingSubscriptions("COMMUNITY", docTokens, "장학금 안내")).isEmpty();
    }

    @Test
    @DisplayName("findByCategoryWithMember 는 학식 구독을 회원과 함께 반환한다")
    void should_find_cafeteria_subscribers() {
        // given
        Member a = 영속회원("caf-a@mju.ac.kr");
        Member b = 영속회원("caf-b@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(a, "학식알림", Set.of(AlarmCategory.CAFETERIA)));
        repository.saveAndFlush(KeywordSubscription.of(b, "장학", Set.of(AlarmCategory.NOTICE))); // 학식 아님
        em.clear();

        // when
        List<KeywordSubscription> result = repository.findByCategoryWithMember(AlarmCategory.CAFETERIA);

        // then - 학식 구독만, 회원 즉시 접근 가능(fetch join)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMember().getEmail()).isEqualTo("caf-a@mju.ac.kr");
    }

    @Test
    @DisplayName("enabled=false 구독은 키워드 매칭에서 제외된다")
    void should_not_match_when_disabled() {
        // given
        Member member = 영속회원("off-match@mju.ac.kr");
        KeywordSubscription subscription = KeywordSubscription.of(member, "장학", Set.of(AlarmCategory.NOTICE));
        subscription.changeEnabled(false);
        repository.saveAndFlush(subscription);
        String docTokens = KomoranTokenizerUtil.buildSearchTokens("2026 교내 장학금 신청 안내", null, "장학금 신청 안내");

        // when & then - off 면 발송 대상 아님
        assertThat(repository.findMatchingSubscriptions(
                "NOTICE", docTokens, "2026 교내 장학금 신청 안내")).isEmpty();
    }

    @Test
    @DisplayName("enabled=false 학식 구독은 방송 대상에서 제외된다")
    void should_exclude_disabled_cafeteria() {
        // given
        Member on = 영속회원("caf-on@mju.ac.kr");
        Member off = 영속회원("caf-off@mju.ac.kr");
        repository.saveAndFlush(KeywordSubscription.of(on, "학식알림", Set.of(AlarmCategory.CAFETERIA)));
        KeywordSubscription disabled = KeywordSubscription.of(off, "학식끔", Set.of(AlarmCategory.CAFETERIA));
        disabled.changeEnabled(false);
        repository.saveAndFlush(disabled);
        em.clear();

        // when
        List<KeywordSubscription> result = repository.findByCategoryWithMember(AlarmCategory.CAFETERIA);

        // then - on 상태만
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMember().getEmail()).isEqualTo("caf-on@mju.ac.kr");
    }

    @Test
    @DisplayName("findByIdAndMember 는 소유자에게만 구독을 반환한다")
    void should_return_only_for_owner() {
        // given
        Member owner = 영속회원("owner@mju.ac.kr");
        Member other = 영속회원("other@mju.ac.kr");
        KeywordSubscription saved = repository.saveAndFlush(
                KeywordSubscription.of(owner, "해외탐방", Set.of(AlarmCategory.NOTICE)));

        // when
        Optional<KeywordSubscription> byOwner = repository.findByIdAndMember(saved.getId(), owner);
        Optional<KeywordSubscription> byOther = repository.findByIdAndMember(saved.getId(), other);

        // then
        assertThat(byOwner).isPresent();
        assertThat(byOther).isEmpty();
    }
}
