package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.exception.DuplicateKeywordException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KeywordSubscriptionServiceTest {

    @Mock
    private KeywordSubscriptionRepository keywordSubscriptionRepository;

    @Mock
    private MemberRepository memberRepository;

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

    private KeywordSubscriptionDTO.Request.Create createRequest(String keyword, Set<AlarmCategory> categories) {
        KeywordSubscriptionDTO.Request.Create request = new KeywordSubscriptionDTO.Request.Create();
        ReflectionTestUtils.setField(request, "keyword", keyword);
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
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "장학")).willReturn(false);
        given(keywordSubscriptionRepository.save(org.mockito.ArgumentMatchers.any(KeywordSubscription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        keywordSubscriptionService.create(EMAIL, createRequest(" 장학 ", Set.of(AlarmCategory.NOTICE)));

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
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "장학")).willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                keywordSubscriptionService.create(EMAIL, createRequest("장학", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(DuplicateKeywordException.class);
        verify(keywordSubscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 MemberNotFoundException")
    void should_throwMemberNotFound_when_없는회원_등록시() {
        // given
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                keywordSubscriptionService.create(EMAIL, createRequest("장학", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    @DisplayName("타인의 구독을 삭제하려 하면 NotFound로 차단된다")
    void should_throwNotFound_when_타인구독_삭제시() {
        // given
        Member member = 회원();
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(99L, member)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.delete(EMAIL, 99L))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
        verify(keywordSubscriptionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("수정 시 키워드가 그대로면 중복검사 없이 카테고리가 교체된다")
    void should_replace카테고리_when_키워드동일_수정시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));

        // when
        KeywordSubscriptionDTO.Response.Detail result = keywordSubscriptionService.update(
                EMAIL, 10L, updateRequest("장학", Set.of(AlarmCategory.MJU_CALENDAR, AlarmCategory.COMMUNITY)));

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
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "수강신청")).willReturn(false);

        // when
        KeywordSubscriptionDTO.Response.Detail result = keywordSubscriptionService.update(
                EMAIL, 10L, updateRequest(" 수강신청 ", Set.of(AlarmCategory.NOTICE)));

        // then - 앞뒤 공백 제거 + 키워드 교체
        assertThat(result.getKeyword()).isEqualTo("수강신청");
    }

    @Test
    @DisplayName("수정 시 이미 등록한 다른 키워드로 바꾸면 DuplicateKeywordException")
    void should_throwDuplicate_when_수정_키워드중복시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));
        given(keywordSubscriptionRepository.existsByMemberAndKeyword(member, "수강신청")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.update(
                EMAIL, 10L, updateRequest("수강신청", Set.of(AlarmCategory.NOTICE))))
                .isInstanceOf(DuplicateKeywordException.class);
    }

    @Test
    @DisplayName("on/off 수정 시 enabled 값이 반영된다")
    void should_toggle_enabled_when_수정시() {
        // given
        Member member = 회원();
        KeywordSubscription subscription = 구독(10L, member, "장학", Set.of(AlarmCategory.NOTICE));
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(10L, member)).willReturn(Optional.of(subscription));

        // when
        KeywordSubscriptionDTO.Response.Detail result =
                keywordSubscriptionService.updateEnabled(EMAIL, 10L, enabledRequest(false));

        // then
        assertThat(result.isEnabled()).isFalse();
        assertThat(subscription.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("타인의 구독을 on/off 하려 하면 NotFound로 차단된다")
    void should_throwNotFound_when_타인구독_onoff시() {
        // given
        Member member = 회원();
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        given(keywordSubscriptionRepository.findByIdAndMember(99L, member)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> keywordSubscriptionService.updateEnabled(EMAIL, 99L, enabledRequest(false)))
                .isInstanceOf(KeywordSubscriptionNotFoundException.class);
    }

    @Test
    @DisplayName("추천 키워드는 고정 6개를 반환한다")
    void should_return추천키워드_when_조회시() {
        // when
        List<String> recommended = keywordSubscriptionService.getRecommendedKeywords();

        // then
        assertThat(recommended)
                .containsExactly("중간고사", "기말고사", "해외탐방", "해외봉사", "수강신청", "졸업유예");
    }
}
