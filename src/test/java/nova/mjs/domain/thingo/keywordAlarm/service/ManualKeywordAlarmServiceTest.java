package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.dto.ManualAlarmDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.DevicePlatform;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.AlarmSourceNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.exception.DeviceTokenNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmSender;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManualKeywordAlarmServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Mock private UnifiedSearchIndexRepository unifiedSearchIndexRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private NotificationHistoryRepository notificationHistoryRepository;
    @Mock private FcmSender fcmSender;

    @InjectMocks private ManualKeywordAlarmService service;

    private static final String EMAIL = "kimgusqls1@gmail.com";
    private static final String KEYWORD = "멘토";

    private Member 회원(Long id) {
        return Member.builder().id(id).email(EMAIL).build();
    }

    /** 대상이 그 키워드를 실제로 구독 중인 상태를 만든다(수동 발송 전제 조건) */
    private void 구독중(Member member, String keyword) {
        KeywordSubscription subscription =
                KeywordSubscription.of(member, keyword, Set.of(AlarmCategory.NOTICE));
        ReflectionTestUtils.setField(subscription, "id", 42L);
        given(keywordSubscriptionRepository.findByMemberOrderByIdDesc(member))
                .willReturn(List.of(subscription));
    }

    private UnifiedSearchIndex 공지(String title) {
        return UnifiedSearchIndex.of(
                "NOTICE:100", "100", "NOTICE", "공지사항",
                title, "본문", "작성자",
                "https://mju.ac.kr/notice/100", null,
                0, 0, 0.0, Instant.now(), null, "멘토링", "멘토링");
    }

    @Test
    @DisplayName("회원+키워드 매칭 콘텐츠가 있으면 알림함 기록 후 해당 토큰으로 FCM 발송한다")
    void should_send_manual_alarm() {
        Member member = 회원(1L);
        UnifiedSearchIndex doc = 공지("2024 멘토링 프로그램 멘토 모집");
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        구독중(member, KEYWORD);
        given(unifiedSearchIndexRepository.findLatestActiveByTitleKeyword(any(), any()))
                .willReturn(Optional.of(doc));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(member, "NOTICE:100"))
                .willReturn(Optional.empty());
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> {
                    NotificationHistory h = invocation.getArgument(0);
                    ReflectionTestUtils.setField(h, "id", 55L);
                    return h;
                });

        ManualAlarmDTO.Response response = service.send(EMAIL, KEYWORD, null);

        ArgumentCaptor<FcmDispatch> captor = ArgumentCaptor.forClass(FcmDispatch.class);
        verify(fcmSender).sendAll(captor.capture());
        FcmDispatch dispatch = captor.getValue();
        assertThat(dispatch.tokens()).containsExactly("tok-1");
        assertThat(dispatch.keywordStyle()).isTrue();
        assertThat(dispatch.title()).isEqualTo(KEYWORD);            // "'멘토' 키워드 새 소식" 으로 렌더
        assertThat(dispatch.body()).isEqualTo(doc.getTitle());       // 본문 = 콘텐츠 제목
        assertThat(dispatch.data()).containsEntry("searchIndexId", "NOTICE:100");

        assertThat(response.getHistoryId()).isEqualTo(55L);
        assertThat(response.getTokenCount()).isEqualTo(1);
        assertThat(response.isPushDispatched()).isTrue();
        assertThat(response.getMatchedTitle()).isEqualTo(doc.getTitle());
    }

    @Test
    @DisplayName("searchIndexId 를 지정하면 키워드 매칭 대신 그 콘텐츠로 발송한다")
    void should_send_specified_content() {
        Member member = 회원(1L);
        UnifiedSearchIndex doc = 공지("2024-2 멘토링 발대식 안내");
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        구독중(member, KEYWORD);
        given(unifiedSearchIndexRepository.findById("NOTICE:100")).willReturn(Optional.of(doc));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(member, "NOTICE:100"))
                .willReturn(Optional.empty());
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ManualAlarmDTO.Response response = service.send(EMAIL, KEYWORD, "NOTICE:100");

        // 키워드 자동 매칭 쿼리는 타지 않아야 한다(지정 콘텐츠 우선)
        verify(unifiedSearchIndexRepository, never()).findLatestActiveByTitleKeyword(any(), any());
        verify(fcmSender).sendAll(any());
        assertThat(response.getSearchIndexId()).isEqualTo("NOTICE:100");
        assertThat(response.getMatchedTitle()).isEqualTo(doc.getTitle());
    }

    @Test
    @DisplayName("이미 발송 내역이 있으면 새 기록을 만들지 않고 재발송한다")
    void should_reuse_existing_history() {
        Member member = 회원(1L);
        UnifiedSearchIndex doc = 공지("멘토 특강 안내");
        NotificationHistory existing = NotificationHistory.of(member, 0L, KEYWORD,
                "NOTICE:100", doc.getTitle(), doc.getLink(), doc.getType());
        ReflectionTestUtils.setField(existing, "id", 77L);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        구독중(member, KEYWORD);
        given(unifiedSearchIndexRepository.findLatestActiveByTitleKeyword(any(), any()))
                .willReturn(Optional.of(doc));
        given(deviceTokenRepository.findByMember(member))
                .willReturn(List.of(DeviceToken.of(member, "tok-1", DevicePlatform.ANDROID)));
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(member, "NOTICE:100"))
                .willReturn(Optional.of(existing));

        ManualAlarmDTO.Response response = service.send(EMAIL, KEYWORD, null);

        verify(notificationHistoryRepository, never()).saveAndFlush(any());
        verify(fcmSender).sendAll(any());
        assertThat(response.getHistoryId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("대상이 등록하지 않은 키워드면 발송하지 않는다(알림함 오염 방지)")
    void should_reject_unsubscribed_keyword() {
        Member member = 회원(1L);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByMemberOrderByIdDesc(member)).willReturn(List.of());

        assertThatThrownBy(() -> service.send(EMAIL, KEYWORD, null))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
        verify(notificationHistoryRepository, never()).saveAndFlush(any());
        verify(fcmSender, never()).sendAll(any());
    }

    @Test
    @DisplayName("구독이 꺼져(enabled=false) 있으면 발송하지 않는다")
    void should_reject_disabled_subscription() {
        Member member = 회원(1L);
        KeywordSubscription off = KeywordSubscription.of(member, KEYWORD, Set.of(AlarmCategory.NOTICE));
        off.changeEnabled(false);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByMemberOrderByIdDesc(member)).willReturn(List.of(off));

        assertThatThrownBy(() -> service.send(EMAIL, KEYWORD, null))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
        verify(fcmSender, never()).sendAll(any());
    }

    @Test
    @DisplayName("대상 회원이 없으면 MemberNotFoundException")
    void should_throw_when_member_missing() {
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(EMAIL, KEYWORD, null))
                .isInstanceOf(MemberNotFoundException.class);
        verify(fcmSender, never()).sendAll(any());
    }

    @Test
    @DisplayName("키워드에 매칭되는 과거 콘텐츠가 없으면 AlarmSourceNotFoundException")
    void should_throw_when_no_content() {
        Member member = 회원(1L);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        구독중(member, KEYWORD);
        given(unifiedSearchIndexRepository.findLatestActiveByTitleKeyword(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(EMAIL, KEYWORD, null))
                .isInstanceOf(AlarmSourceNotFoundException.class);
        verify(fcmSender, never()).sendAll(any());
    }

    @Test
    @DisplayName("대상 회원의 기기 토큰이 없으면 DeviceTokenNotFoundException(보낼 곳 없음)")
    void should_throw_when_no_device_token() {
        Member member = 회원(1L);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        구독중(member, KEYWORD);
        given(unifiedSearchIndexRepository.findLatestActiveByTitleKeyword(any(), any()))
                .willReturn(Optional.of(공지("멘토 모집")));
        given(deviceTokenRepository.findByMember(member)).willReturn(List.of());

        assertThatThrownBy(() -> service.send(EMAIL, KEYWORD, null))
                .isInstanceOf(DeviceTokenNotFoundException.class);
        verify(fcmSender, never()).sendAll(any());
    }
}
