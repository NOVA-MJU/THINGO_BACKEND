package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.DuplicateKeywordException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionLimitExceededException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.semantic.TopicCatalog;
import nova.mjs.domain.thingo.semantic.TopicDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KeywordSubscriptionServiceTest {

    @Mock
    private KeywordSubscriptionRepository keywordSubscriptionRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Mock
    private TopicCatalog topicCatalog;

    @InjectMocks
    private KeywordSubscriptionService keywordSubscriptionService;

    private static final String EMAIL = "user@mju.ac.kr";

    private Member 회원() {
        return Member.builder().id(1L).email(EMAIL).build();
    }

    private KeywordSubscription 구독(Long id, Member member, String keyword, Set<AlarmCategory> categories) {
        KeywordSubscription subscription = KeywordSubscription.of(member, keyword, categories);
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private static final UUID DEVICE_ID = UUID.fromString("7b1c2d3e-4f50-4a61-8b72-9c83d4e5f601");

    private KeywordSubscription 기기구독(Long id, String keyword, Set<AlarmCategory> categories) {
        KeywordSubscription subscription =
                KeywordSubscription.of(AlarmOwner.ofDevice(DEVICE_ID), keyword, null, categories);
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private NotificationHistory 기기알림(String searchIndexId) {
        return NotificationHistory.of(AlarmOwner.ofDevice(DEVICE_ID), 1L, "장학",
                searchIndexId, "제목", "/notice", "NOTICE");
    }

    private KeywordSubscriptionDTO.Request.Create createRequest(String keyword, Set<AlarmCategory> categories) {
        return createRequest(keyword, null, categories);
    }

    private KeywordSubscriptionDTO.Request.Create createRequest(
            String keyword, String topicId, Set<AlarmCategory> categories) {
        KeywordSubscriptionDTO.Request.Create request = new KeywordSubscriptionDTO.Request.Create();
        ReflectionTestUtils.setField(request, "keyword", keyword);
        ReflectionTestUtils.setField(request, "topicId", topicId);
        ReflectionTestUtils.setField(request, "categories", categories);
        return request;
    }

    private KeywordSubscriptionDTO.Request.Update updateRequest(String keyword, Set<AlarmCategory> categories) {
        KeywordSubscriptionDTO.Request.Update request = new KeywordSubscriptionDTO.Request.Update();
        ReflectionTestUtils.setField(request, "keyword", keyword);
        ReflectionTestUtils.setField(request, "categories", categories);
        return request;
    }

    private KeywordSubscriptionDTO.Request.UpdateEnabled enabledRequest(boolean enabled) {
        KeywordSubscriptionDTO.Request.UpdateEnabled request = new KeywordSubscriptionDTO.Request.UpdateEnabled();
        ReflectionTestUtils.setField(request, "enabled", enabled);
        return request;
    }

    @Test
    @DisplayName("키워드 앞뒤 공백을 제거하고 저장한다")
    void should_trim키워드_when_등록시() {
        // given
        Member member = 회원();
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "장학")).willReturn(false);
        given(keywordSubscriptionRepository.save(org.mockito.ArgumentMatchers.any(KeywordSubscription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        keywordSubscriptionService.create(AlarmOwner.ofMember(member), createRequest(" 장학 ", Set.of(AlarmCategory.NOTICE)));

        // then
        ArgumentCaptor<KeywordSubscription> captor = ArgumentCaptor.forClass(KeywordSubscription.class);
        verify(keywordSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("장학");
    }

    @Test
    @DisplayName("같은 키워드를 다시 등록하면 DuplicateKeywordException")
    void should_throwDuplicate_when_중복키워드_등록시() {
        // given
        Member member = 회원();
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "장학")).willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                keywordSubscriptionService.create(AlarmOwner.ofMember(member), createRequest("장학", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(DuplicateKeywordException.class);
        verify(keywordSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("타인의 구독을 삭제하려 하면 NotFound로 차단된다")
    void should_throwNotFound_when_타인구독_삭제시() {
        // given
        Member member = 회원();
        given(keywordSubscriptionRepository.findByIdAndMember(99L, member)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.delete(AlarmOwner.ofMember(member), 99L))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
        verify(keywordSubscriptionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("수정 시 키워드가 그대로면 중복검사 없이 카테고리가 교체된다")
    void should_replace카테고리_when_키워드동일_수정시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));

        // when
        KeywordSubscriptionDTO.Response.Detail result = keywordSubscriptionService.update(AlarmOwner.ofMember(member), 10L, updateRequest("장학", Set.of(AlarmCategory.MJU_CALENDAR, AlarmCategory.COMMUNITY)));

        // then - 키워드 동일 -> 중복검사 미호출, 카테고리만 교체
        assertThat(result.getKeyword()).isEqualTo("장학");
        assertThat(result.getCategories())
                .containsExactlyInAnyOrder(AlarmCategory.MJU_CALENDAR, AlarmCategory.COMMUNITY);
        verify(keywordSubscriptionRepository, never())
                .existsByMemberAndKeyword(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("수정 시 키워드를 유일한 값으로 바꾸면 키워드가 교체된다")
    void should_change키워드_when_수정시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "수강신청")).willReturn(false);

        // when
        KeywordSubscriptionDTO.Response.Detail result = keywordSubscriptionService.update(AlarmOwner.ofMember(member), 10L, updateRequest(" 수강신청 ", Set.of(AlarmCategory.NOTICE)));

        // then - 앞뒤 공백 제거 + 키워드 교체
        assertThat(result.getKeyword()).isEqualTo("수강신청");
    }

    @Test
    @DisplayName("수정 시 이미 등록한 다른 키워드로 바꾸면 DuplicateKeywordException")
    void should_throwDuplicate_when_수정_키워드중복시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "수강신청")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.update(AlarmOwner.ofMember(member), 10L, updateRequest("수강신청", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(DuplicateKeywordException.class);
    }

    @Test
    @DisplayName("on/off 수정 시 enabled 값이 반영된다")
    void should_toggle_enabled_when_수정시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));

        // when
        KeywordSubscriptionDTO.Response.Detail result =
                keywordSubscriptionService.updateEnabled(AlarmOwner.ofMember(member), 10L, enabledRequest(false));

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(subscription.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("타인의 구독을 on/off 하려 하면 NotFound로 차단된다")
    void should_throwNotFound_when_타인구독_onoff시() {
        // given
        Member member = 회원();
        given(keywordSubscriptionRepository.findByIdAndMember(99L, member)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.updateEnabled(AlarmOwner.ofMember(member), 99L, enabledRequest(false)))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
    }

    @Test
    @DisplayName("추천 키워드는 고정 6개를 반환한다")
    void should_return추천키워드_when_조회시() {
        // when
        List<String> recommended = keywordSubscriptionService.getRecommendedKeywords();

        // then
        assertThat(recommended)
                .containsExactly("수강신청", "기숙사", "졸업", "국가근로", "해외탐방", "해외봉사");
    }

    @Test
    @DisplayName("자동완성에서 선택한 Topic ID를 키워드와 함께 저장한다")
    void should_save_topic_id_when_autocomplete_selected() {
        Member member = 회원();
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "졸업")).willReturn(false);
        given(topicCatalog.find("GRADUATION")).willReturn(Optional.of(new TopicDefinition(
                "GRADUATION", null, "졸업 전체", "졸업 관련 공지 전체",
                true, true, true, List.of("졸업"))));
        given(keywordSubscriptionRepository.save(org.mockito.ArgumentMatchers.any(KeywordSubscription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        KeywordSubscriptionDTO.Response.Detail result = keywordSubscriptionService.create(AlarmOwner.ofMember(member), createRequest("졸업", "GRADUATION", Set.of(AlarmCategory.NOTICE)));

        assertThat(result.getTopicId()).isEqualTo("GRADUATION");
    }

    @Test
    @DisplayName("비로그인 기기도 키워드를 등록할 수 있고 구독은 기기 소유로 저장된다")
    void should_save_device_owned_subscription_when_게스트_등록시() {
        // given
        given(keywordSubscriptionRepository.existsByDeviceIdAndKeyword(DEVICE_ID, "장학")).willReturn(false);
        given(keywordSubscriptionRepository.countByDeviceId(DEVICE_ID)).willReturn(3L);
        given(keywordSubscriptionRepository.save(any(KeywordSubscription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        keywordSubscriptionService.create(AlarmOwner.ofDevice(DEVICE_ID), createRequest("장학", Set.of(AlarmCategory.NOTICE)));

        // then
        ArgumentCaptor<KeywordSubscription> captor = ArgumentCaptor.forClass(KeywordSubscription.class);
        verify(keywordSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isNull();
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("비로그인 기기의 목록은 그 기기 구독만 조회한다")
    void should_list_device_subscriptions_when_게스트_조회시() {
        given(keywordSubscriptionRepository.findByDeviceIdOrderByIdDesc(DEVICE_ID))
                .willReturn(List.of(기기구독(5L, "장학", Set.of(AlarmCategory.NOTICE))));

        List<KeywordSubscriptionDTO.Response.Detail> result =
                keywordSubscriptionService.getMySubscriptions(AlarmOwner.ofDevice(DEVICE_ID));

        assertThat(result).extracting(KeywordSubscriptionDTO.Response.Detail::getKeyword).containsExactly("장학");
        verify(keywordSubscriptionRepository, never()).findByMemberOrderByIdDesc(any());
    }

    @Test
    @DisplayName("다른 기기의 구독은 삭제할 수 없다")
    void should_throwNotFound_when_다른기기구독_삭제시() {
        given(keywordSubscriptionRepository.findByIdAndDeviceId(99L, DEVICE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> keywordSubscriptionService.delete(AlarmOwner.ofDevice(DEVICE_ID), 99L))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
        verify(keywordSubscriptionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("소유자당 10개를 넘겨 등록하면 KeywordSubscriptionLimitExceededException")
    void should_throwLimit_when_10개초과_등록시() {
        Member member = 회원();
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "장학")).willReturn(false);
        given(keywordSubscriptionRepository.countByMember(member)).willReturn(10L);
        given(keywordSubscriptionRepository.existsByDeviceIdAndKeyword(DEVICE_ID, "장학")).willReturn(false);
        given(keywordSubscriptionRepository.countByDeviceId(DEVICE_ID)).willReturn(10L);

        assertThatThrownBy(() -> keywordSubscriptionService.create(
                AlarmOwner.ofMember(member), createRequest("장학", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(KeywordSubscriptionLimitExceededException.class);
        assertThatThrownBy(() -> keywordSubscriptionService.create(
                AlarmOwner.ofDevice(DEVICE_ID), createRequest("장학", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(KeywordSubscriptionLimitExceededException.class);
        verify(keywordSubscriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("병합: 같은 키워드(대소문자/공백 무시)는 카테고리를 합치고 기기 구독을 지우며, 새 키워드는 회원으로 옮긴다")
    void should_union_duplicate_and_reassign_new_when_병합시() {
        // given - 회원: "ai"(NOTICE, off) / 기기: "AI"(COMMUNITY, on), "장학"(NOTICE)
        Member member = 회원();
        KeywordSubscription memberAi = 구독(1L, member, "ai", Set.of(AlarmCategory.NOTICE));
        memberAi.changeEnabled(false);
        KeywordSubscription deviceAi = 기기구독(11L, "AI", Set.of(AlarmCategory.COMMUNITY));
        KeywordSubscription deviceScholarship = 기기구독(12L, "장학", Set.of(AlarmCategory.NOTICE));
        given(keywordSubscriptionRepository.findByMemberOrderByIdDesc(member)).willReturn(List.of(memberAi));
        given(keywordSubscriptionRepository.findByDeviceIdOrderByIdDesc(DEVICE_ID))
                .willReturn(List.of(deviceScholarship, deviceAi));

        // when
        KeywordSubscriptionDTO.Response.MergeResult result = keywordSubscriptionService.mergeDevice(member, DEVICE_ID);

        // then
        assertThat(result.getMergedCount()).isEqualTo(1);
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(memberAi.getCategories())
                .containsExactlyInAnyOrder(AlarmCategory.NOTICE, AlarmCategory.COMMUNITY);
        assertThat(memberAi.isEnabled()).isTrue();
        verify(keywordSubscriptionRepository).delete(deviceAi);
        assertThat(deviceScholarship.getMember()).isEqualTo(member);
        assertThat(deviceScholarship.getDeviceId()).isNull();
    }

    @Test
    @DisplayName("병합: 회원 구독이 10개에 차면 새 키워드는 옮기지 않고 skippedCount 로 알린다(중복 합치기는 계속)")
    void should_skip_new_keywords_over_limit_when_병합시() {
        // given - 회원 10개(키워드0~키워드9), 기기: 오래된 "새키1", 최신 "키워드3"(중복)
        Member member = 회원();
        List<KeywordSubscription> memberSubscriptions = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            memberSubscriptions.add(구독((long) i, member, "키워드" + i, Set.of(AlarmCategory.NOTICE)));
        }
        KeywordSubscription deviceNew = 기기구독(21L, "새키1", Set.of(AlarmCategory.NOTICE));
        KeywordSubscription deviceDup = 기기구독(22L, "키워드3", Set.of(AlarmCategory.MJU_CALENDAR));
        given(keywordSubscriptionRepository.findByMemberOrderByIdDesc(member)).willReturn(memberSubscriptions);
        given(keywordSubscriptionRepository.findByDeviceIdOrderByIdDesc(DEVICE_ID))
                .willReturn(List.of(deviceDup, deviceNew));

        // when
        KeywordSubscriptionDTO.Response.MergeResult result = keywordSubscriptionService.mergeDevice(member, DEVICE_ID);

        // then - 새 키워드는 기기에 남고, 중복은 합쳐진다
        assertThat(result.getMergedCount()).isZero();
        assertThat(result.getDuplicateCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(deviceNew.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(deviceNew.getMember()).isNull();
        assertThat(memberSubscriptions.get(3).getCategories())
                .containsExactlyInAnyOrder(AlarmCategory.NOTICE, AlarmCategory.MJU_CALENDAR);
    }

    @Test
    @DisplayName("병합: 기기 알림함은 회원이 이미 받은 콘텐츠를 제외하고 회원에게 옮긴다")
    void should_move_device_notifications_when_병합시() {
        Member member = 회원();
        NotificationHistory already = 기기알림("NOTICE:1");
        NotificationHistory fresh = 기기알림("NOTICE:2");
        given(notificationHistoryRepository.findByDeviceId(DEVICE_ID)).willReturn(List.of(already, fresh));
        given(notificationHistoryRepository.findSearchIndexIdsByMember(any(), any())).willReturn(List.of("NOTICE:1"));

        keywordSubscriptionService.mergeDevice(member, DEVICE_ID);

        verify(notificationHistoryRepository).delete(already);
        assertThat(fresh.getMember()).isEqualTo(member);
        assertThat(fresh.getDeviceId()).isNull();
    }

    @Test
    @DisplayName("병합: 기기에 남은 것이 없으면 아무 것도 바꾸지 않는다(재호출 안전)")
    void should_be_noop_when_병합할것없음() {
        Member member = 회원();

        KeywordSubscriptionDTO.Response.MergeResult result = keywordSubscriptionService.mergeDevice(member, DEVICE_ID);

        assertThat(result.getMergedCount()).isZero();
        assertThat(result.getDuplicateCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
        verify(keywordSubscriptionRepository, never()).delete(any());
        verify(notificationHistoryRepository, never()).findSearchIndexIdsByMember(any(), any());
    }
}
