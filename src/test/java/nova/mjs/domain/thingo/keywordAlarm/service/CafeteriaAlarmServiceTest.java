package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.DevicePlatform;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CafeteriaAlarmServiceTest {

    @Mock private KeywordSubscriptionRepository keywordSubscriptionRepository;
    @Mock private NotificationHistoryRepository notificationHistoryRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private RedisTemplate<String, String> keywordRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private CafeteriaAlarmService service() {
        return new CafeteriaAlarmService(keywordSubscriptionRepository, notificationHistoryRepository,
                deviceTokenRepository, keywordRedisTemplate);
    }

    private Member 회원(Long id) {
        return Member.builder().id(id).email("u" + id + "@mju.ac.kr").build();
    }

    private KeywordSubscription 학식구독(Long id, Member member) {
        KeywordSubscription subscription =
                KeywordSubscription.of(member, "학식알림", Set.of(AlarmCategory.CAFETERIA));
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    @Test
    @DisplayName("새 학식이면 구독자 회원마다 1건씩 발송 단위를 만든다")
    void should_broadcast_per_member() {
        // given - 회원1(학식구독 2개), 회원2(1개)
        Member m1 = 회원(1L);
        Member m2 = 회원(2L);
        given(keywordSubscriptionRepository.findByCategoryWithMember(AlarmCategory.CAFETERIA))
                .willReturn(List.of(학식구독(10L, m1), 학식구독(11L, m1), 학식구독(12L, m2)));
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(any())).willReturn("old-sig"); // 직전과 다름 -> 새 내용
        given(notificationHistoryRepository.saveAndFlush(any(NotificationHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(deviceTokenRepository.findByMember(m1))
                .willReturn(List.of(DeviceToken.of(m1, "tok-1", DevicePlatform.ANDROID)));
        given(deviceTokenRepository.findByMember(m2))
                .willReturn(List.of(DeviceToken.of(m2, "tok-2", DevicePlatform.IOS)));

        // when
        List<FcmDispatch> result = service().broadcastIfNew(15, "new-sig");

        // then - 회원 2명 -> 내역 2건, 발송 2건(회원1은 구독 2개라도 1건)
        verify(notificationHistoryRepository, times(2)).saveAndFlush(any(NotificationHistory.class));
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("내용 지문이 직전과 같으면(같은 주 반복 크롤링) 발송하지 않는다")
    void should_skip_when_same_signature() {
        given(keywordRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(any())).willReturn("same-sig");

        List<FcmDispatch> result = service().broadcastIfNew(15, "same-sig");

        assertThat(result).isEmpty();
        verify(keywordSubscriptionRepository, never()).findByCategoryWithMember(any());
        verify(notificationHistoryRepository, never()).saveAndFlush(any());
    }
}
