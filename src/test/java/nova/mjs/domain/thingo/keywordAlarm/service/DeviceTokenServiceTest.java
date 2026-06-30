package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceTokenDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.DevicePlatform;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private DeviceTokenService deviceTokenService;

    private Member 회원(Long id, String email) {
        return Member.builder().id(id).email(email).build();
    }

    private DeviceTokenDTO.Request.Register 등록요청(String token, DevicePlatform platform) {
        DeviceTokenDTO.Request.Register request = new DeviceTokenDTO.Request.Register();
        ReflectionTestUtils.setField(request, "fcmToken", token);
        ReflectionTestUtils.setField(request, "platform", platform);
        return request;
    }

    @Test
    @DisplayName("새 토큰이면 저장한다")
    void should_save_when_새토큰() {
        Member member = 회원(1L, "a@mju.ac.kr");
        given(memberRepository.findByEmail("a@mju.ac.kr")).willReturn(Optional.of(member));
        given(deviceTokenRepository.findByFcmToken("tok-1")).willReturn(Optional.empty());

        deviceTokenService.register("a@mju.ac.kr", 등록요청("tok-1", DevicePlatform.ANDROID));

        verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("같은 토큰이 다른 회원 소유면 현재 회원으로 재배정한다")
    void should_reassign_when_타회원_토큰() {
        Member owner = 회원(1L, "owner@mju.ac.kr");
        Member newMember = 회원(2L, "new@mju.ac.kr");
        DeviceToken existing = DeviceToken.of(owner, "tok-shared", DevicePlatform.IOS);

        given(memberRepository.findByEmail("new@mju.ac.kr")).willReturn(Optional.of(newMember));
        given(deviceTokenRepository.findByFcmToken("tok-shared")).willReturn(Optional.of(existing));

        deviceTokenService.register("new@mju.ac.kr", 등록요청("tok-shared", DevicePlatform.ANDROID));

        assertThat(existing.isOwnedBy(newMember)).isTrue();
        assertThat(existing.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("본인 소유 토큰만 삭제한다")
    void should_delete_only_owned() {
        Member member = 회원(1L, "a@mju.ac.kr");
        Member other = 회원(2L, "b@mju.ac.kr");
        DeviceToken othersToken = DeviceToken.of(other, "tok-x", DevicePlatform.WEB);

        given(memberRepository.findByEmail("a@mju.ac.kr")).willReturn(Optional.of(member));
        given(deviceTokenRepository.findByFcmToken("tok-x")).willReturn(Optional.of(othersToken));

        deviceTokenService.delete("a@mju.ac.kr", "tok-x");

        verify(deviceTokenRepository, never()).delete(any(DeviceToken.class));
    }
}
