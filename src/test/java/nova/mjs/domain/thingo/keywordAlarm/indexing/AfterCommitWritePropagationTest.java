package nova.mjs.domain.thingo.keywordAlarm.indexing;

import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
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
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AFTER_COMMIT 리스너 안에서의 DB 쓰기 전파 동작 검증.
 *
 * KeywordAlarmIndexListener 는 AFTER_COMMIT 에서 KeywordMatchingService.matchAndCollect 를 호출하고,
 * 그 안에서 NotificationHistory 를 저장한다. 이때 전파 방식에 따라 쓰기가 살아남는지 달라진다.
 * 형제 리스너(PgUnifiedSearchIndexListener)는 REQUIRES_NEW 를 쓴다.
 */
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
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@Import(AfterCommitWritePropagationTest.Fixture.class)
class AfterCommitWritePropagationTest {

    record TriggerEvent(String tag) {
    }

    @TestConfiguration
    static class Fixture {

        @Service
        static class Writer {

            @Autowired
            private MemberRepository memberRepository;

            /** 현재 KeywordMatchingService.matchAndCollect 와 같은 전파(REQUIRED) */
            @Transactional
            public void writeWithRequired(String email) {
                memberRepository.saveAndFlush(member(email));
            }

            /** PgUnifiedSearchIndexListener 와 같은 전파(REQUIRES_NEW) */
            @Transactional(propagation = Propagation.REQUIRES_NEW)
            public void writeWithRequiresNew(String email) {
                memberRepository.saveAndFlush(member(email));
            }

            private Member member(String email) {
                return Member.builder()
                        .uuid(UUID.randomUUID())
                        .role(Member.Role.USER)
                        .name("테스터")
                        .email(email)
                        .password("encoded")
                        .college(College.AI_SOFTWARE)
                        .build();
            }
        }

        @Component
        static class AfterCommitListener {

            /** REQUIRED 쓰기가 던진 예외. KeywordAlarmIndexListener 의 catch 블록에 해당한다. */
            static volatile Throwable requiredFailure;

            @Autowired
            private Writer writer;

            @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
            public void on(TriggerEvent event) {
                requiredFailure = null;
                try {
                    writer.writeWithRequired("required@mju.ac.kr");
                } catch (Exception e) {
                    requiredFailure = e; // 운영 리스너와 동일하게 삼켜진다
                }
                writer.writeWithRequiresNew("requiresnew@mju.ac.kr");
            }
        }

        @Service
        static class Trigger {

            @Autowired
            private ApplicationEventPublisher publisher;

            @Transactional
            public void fire() {
                publisher.publishEvent(new TriggerEvent("notice-insert"));
            }
        }
    }

    @Autowired
    private Fixture.Trigger trigger;

    @Autowired
    private MemberRepository memberRepository;

    @MockBean
    private SearchIndexPublisher searchIndexPublisher;

    @Test
    @DisplayName("AFTER_COMMIT 안의 쓰기는 REQUIRES_NEW 일 때만 커밋된다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 테스트 자체 트랜잭션/롤백 제거
    void writes_inside_after_commit() {
        trigger.fire();

        boolean requiredSurvived = memberRepository.findByEmail("required@mju.ac.kr").isPresent();
        boolean requiresNewSurvived = memberRepository.findByEmail("requiresnew@mju.ac.kr").isPresent();
        Throwable requiredFailure = Fixture.AfterCommitListener.requiredFailure;

        System.out.println("[AFTER_COMMIT 쓰기] REQUIRED 커밋됨=" + requiredSurvived
                + ", REQUIRES_NEW 커밋됨=" + requiresNewSurvived
                + ", REQUIRED 예외=" + (requiredFailure == null ? "없음" : requiredFailure.getMessage()));

        assertThat(requiredSurvived)
                .as("REQUIRED 는 이미 커밋된 트랜잭션에 참여해 저장에 실패한다")
                .isFalse();
        assertThat(requiredFailure)
                .as("no transaction is in progress 로 터진다(운영에서는 리스너 catch 에 삼켜짐)")
                .isNotNull();
        assertThat(requiresNewSurvived)
                .as("REQUIRES_NEW 는 새 트랜잭션이라 정상 커밋된다")
                .isTrue();
    }
}
