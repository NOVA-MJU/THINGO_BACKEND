package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.keywordAlarm.entity.DevicePlatform;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordMatch;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.semantic.NoticeSemanticClassifier;
import nova.mjs.domain.thingo.semantic.TopicCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KeywordMatchingServiceTest {

    @Mock private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Mock private NotificationHistoryRepository notificationHistoryRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private RedisTemplate<String, String> keywordRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private KeywordMatchingService service() {
        return new KeywordMatchingService(keywordSubscriptionRepository, notificationHistoryRepository,
                deviceTokenRepository, memberRepository, keywordRedisTemplate,
                new NoticeSemanticClassifier(new TopicCatalog()),
                new AlarmMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    private SearchDocument doc(String id, String type, String title, String content) {
        return new SearchDocument() {
            public String getId() { return id; }
            public String getTitle() { return title; }
            public String getContent() { return content; }
            public String getType() { return type; }
            public Instant getInstant() { return Instant.now(); }
            public String getLink() { return "https://example.com/" + id; }
        };
    }

    @Test
    @DisplayName("MVP 카테고리 외 타입(NEWS)은 매칭하지 않는다")
    void should_skip_when_제외타입() {
        List<FcmDispatch> result = service().matchAndCollect(doc("1", "NEWS", "장학금 안내", "본문"));

        assertThat(result).isEmpty();
        verifyNoInteractions(keywordSubscriptionRepository, notificationHistoryRepository, deviceTokenRepository);
    }

    @Test
    @DisplayName("매칭 시 내역을 저장하고 기기 토큰으로 발송 단위를 만든다")
    void should_save_history_and_collect_tokens() {
        // given - NOTICE 문서가 "장학" 구독과 매칭
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString(), anyString()))
                .willReturn(List.of(new KeywordMatch(10L, 1L, "장학")));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

        Member member = Member.builder().id(1L).email("u@mju.ac.kr").build();
        given(memberRepository.getReferenceById(1L)).willReturn(member);
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));

        // when
        List<FcmDispatch> result = service().matchAndCollect(doc("100", "NOTICE", "교내 장학 신청 안내", "장학금 신청"));

        // then
        verify(notificationHistoryRepository).saveAndFlush(any(NotificationHistory.class));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).tokens()).containsExactly("tok-1");
        assertThat(result.get(0).title()).isEqualTo("장학");
    }

    @Test
    @DisplayName("같은 공지가 다른 게시판에 별도 글로 올라오면(교차게시) 최근 받은 회원에게 다시 보내지 않는다")
    void should_skip_cross_posted_notice() {
        // given - 회원 1이 "[진로취업지원팀] ..." 을 이미 받았고, 같은 공지가 부서명 없이 다른 게시판에 또 올라옴
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString(), anyString()))
                .willReturn(List.of(new KeywordMatch(37L, 1L, "모집")));
        given(notificationHistoryRepository.findRecentTitles(eq(1L), any(Instant.class)))
                .willReturn(List.of("[진로취업지원팀] 2026 선배와의 취업멘토링 참여학생 모집(10월)"));

        // when
        List<FcmDispatch> result = service().matchAndCollect(
                doc("2921", "NOTICE", "2026 선배와의 취업멘토링 참여학생 모집(10월)", "본문"));

        // then - 내역 저장/발송 없음, Redis claim 도 하지 않음
        assertThat(result).isEmpty();
        verify(notificationHistoryRepository, never()).saveAndFlush(any(NotificationHistory.class));
        verifyNoInteractions(keywordRedisTemplate);
    }

    @Test
    @DisplayName("제목 비교 키는 앞머리 [..] 와 공백·기호 차이를 무시한다")
    void core_title_ignores_prefix_and_symbols() {
        assertThat(KeywordMatchingService.coreTitle("[혁신사업-에너지 소재 사업단] 2026학년도 에너지 소재 전문가 특강(6차)"))
                .isEqualTo(KeywordMatchingService.coreTitle("2026학년도 에너지 소재 전문가 특강 (6차)"));
        assertThat(KeywordMatchingService.coreTitle("[A][B] 모집 안내"))
                .isEqualTo(KeywordMatchingService.coreTitle("모집안내"));
        // 본문 제목이 다르면 다른 공지
        assertThat(KeywordMatchingService.coreTitle("[학생지원팀] 해외봉사 합격자 안내"))
                .isNotEqualTo(KeywordMatchingService.coreTitle("[학생지원팀] 해외봉사 참가자 선발 안내"));
    }

    @Test
    @DisplayName("한 콘텐츠가 한 회원의 키워드 여러 개에 걸려도 알림은 1건으로 합친다")
    void should_merge_per_member() {
        // given - 같은 회원(1L)의 "장학", "신청" 두 구독이 모두 매칭
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString(), anyString()))
                .willReturn(List.of(
                        new KeywordMatch(10L, 1L, "장학"),
                        new KeywordMatch(11L, 1L, "신청")));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

        Member member = Member.builder().id(1L).email("u@mju.ac.kr").build();
        given(memberRepository.getReferenceById(1L)).willReturn(member);
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));

        // when
        List<FcmDispatch> result = service().matchAndCollect(doc("100", "NOTICE", "장학 신청 안내", "장학금 신청"));

        // then - 내역 1건, 발송 단위 1건(대표 키워드)
        verify(notificationHistoryRepository, times(1)).saveAndFlush(any(NotificationHistory.class));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Redis dedup 가 이미 발송됨을 알리면 내역을 저장하지 않는다")
    void should_skip_when_dedup_hit() {
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString(), anyString()))
                .willReturn(List.of(new KeywordMatch(10L, 1L, "장학")));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(false);

        List<FcmDispatch> result = service().matchAndCollect(doc("100", "NOTICE", "교내 장학 신청 안내", "장학금 신청"));

        assertThat(result).isEmpty();
        verify(notificationHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("상위 Topic 구독은 하위 Topic으로 분류된 신규 공지와 매칭한다")
    void should_match_parent_topic_subscription() {
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString(), anyString()))
                .willReturn(List.of());
        given(keywordSubscriptionRepository.findMatchingTopicSubscriptions(
                eq(nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory.NOTICE), any()))
                .willReturn(List.of(new KeywordMatch(20L, 1L, "졸업", "GRADUATION")));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

        Member member = Member.builder().id(1L).email("u@mju.ac.kr").build();
        given(memberRepository.getReferenceById(1L)).willReturn(member);
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));

        List<FcmDispatch> result = service().matchAndCollect(
                doc("200", "NOTICE", "학사학위취득유예 신청 안내", ""));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("졸업");
        verify(keywordSubscriptionRepository).findMatchingTopicSubscriptions(
                eq(nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory.NOTICE),
                org.mockito.ArgumentMatchers.argThat(ids ->
                        ids.contains("GRADUATION") && ids.contains("GRADUATION_DEFERRAL")));
    }
}
