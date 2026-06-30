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
                deviceTokenRepository, memberRepository, keywordRedisTemplate);
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
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString()))
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
    @DisplayName("한 콘텐츠가 한 회원의 키워드 여러 개에 걸려도 알림은 1건으로 합친다")
    void should_merge_per_member() {
        // given - 같은 회원(1L)의 "장학", "신청" 두 구독이 모두 매칭
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString()))
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
        given(keywordSubscriptionRepository.findMatchingSubscriptions(eq("NOTICE"), anyString()))
                .willReturn(List.of(new KeywordMatch(10L, 1L, "장학")));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(false);

        List<FcmDispatch> result = service().matchAndCollect(doc("100", "NOTICE", "교내 장학 신청 안내", "장학금 신청"));

        assertThat(result).isEmpty();
        verify(notificationHistoryRepository, never()).saveAndFlush(any());
    }
}
