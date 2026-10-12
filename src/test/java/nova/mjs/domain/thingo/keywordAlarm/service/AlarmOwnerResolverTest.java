package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.exception.InvalidDeviceIdException;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.util.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlarmOwnerResolverTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private DeviceService deviceService;
    @InjectMocks
    private AlarmOwnerResolver resolver;

    private static final String EMAIL = "user@mju.ac.kr";
    private static final UUID DEVICE_ID = UUID.randomUUID();

    private UserPrincipal 로그인() {
        return new UserPrincipal(EMAIL, "USER");
    }

    @Test
    @DisplayName("로그인 상태면 X-Device-Id 가 있어도 회원이 소유자다")
    void should_resolve_member_when_로그인() {
        Member member = Member.builder().id(1L).email(EMAIL).build();
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));

        AlarmOwner owner = resolver.resolve(로그인(), DEVICE_ID.toString());

        assertThat(owner.isMember()).isTrue();
        assertThat(owner.member()).isEqualTo(member);
        verify(deviceService, never()).findIssued(any());
    }

    @Test
    @DisplayName("비로그인 + 발급된 기기 id 면 기기가 소유자다")
    void should_resolve_device_when_비로그인_유효기기() {
        given(deviceService.findIssued(DEVICE_ID.toString())).willReturn(Optional.of(DEVICE_ID));

        AlarmOwner owner = resolver.resolve(null, DEVICE_ID.toString());

        assertThat(owner.isMember()).isFalse();
        assertThat(owner.deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("비로그인인데 기기 id 가 없거나 발급되지 않은 값이면 InvalidDeviceIdException")
    void should_throw_when_비로그인_기기없음() {
        given(deviceService.findIssued(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(null, null))
                .isInstanceOf(InvalidDeviceIdException.class);
        assertThatThrownBy(() -> resolver.resolve(null, UUID.randomUUID().toString()))
                .isInstanceOf(InvalidDeviceIdException.class);
    }

    @Test
    @DisplayName("로그인 회원을 찾을 수 없으면 MemberNotFoundException")
    void should_throw_when_회원없음() {
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(로그인(), null))
                .isInstanceOf(MemberNotFoundException.class);
    }
}
