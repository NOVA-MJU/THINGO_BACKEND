package nova.mjs.domain.thingo.member.google;

import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.DuplicateNicknameException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.util.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAuthServiceTest {

    private static final GoogleAccount ACCOUNT = new GoogleAccount("hong@mju.ac.kr", "홍길동", "https://pic");

    private GoogleIdTokenService idTokenService;
    private GoogleSignupTicketStore ticketStore;
    private MemberRepository memberRepository;
    private MemberQueryService memberQueryService;
    private GoogleAuthService service;

    @BeforeEach
    void setUp() {
        idTokenService = mock(GoogleIdTokenService.class);
        ticketStore = mock(GoogleSignupTicketStore.class);
        memberRepository = mock(MemberRepository.class);
        memberQueryService = mock(MemberQueryService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateAccessToken(any(), anyString(), anyString())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any(), anyString(), anyString())).thenReturn("refresh");
        when(idTokenService.verify("id-token")).thenReturn(ACCOUNT);
        service = new GoogleAuthService(idTokenService, ticketStore, memberRepository, memberQueryService, jwtUtil);
    }

    @Test
    void 같은_이메일_회원이_있으면_이메일_가입자여도_바로_로그인한다() {
        Member existing = Member.builder().uuid(UUID.randomUUID()).email("hong@mju.ac.kr")
                .password("{bcrypt}x").role(Member.Role.USER).build();
        when(memberRepository.findByEmail("hong@mju.ac.kr")).thenReturn(Optional.of(existing));

        GoogleAuthDTO.LoginResult result = service.login("id-token");

        assertThat(result.getStatus()).isEqualTo(GoogleAuthDTO.Status.LOGGED_IN);
        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
        assertThat(result.getSignupToken()).isNull();
    }

    @Test
    void 처음_온_학교_계정이면_가입표와_구글_계정_정보를_준다() {
        when(memberRepository.findByEmail("hong@mju.ac.kr")).thenReturn(Optional.empty());
        when(ticketStore.issue(ACCOUNT)).thenReturn("ticket");

        GoogleAuthDTO.LoginResult result = service.login("id-token");

        assertThat(result.getStatus()).isEqualTo(GoogleAuthDTO.Status.SIGNUP_REQUIRED);
        assertThat(result.getSignupToken()).isEqualTo("ticket");
        assertThat(result.getEmail()).isEqualTo("hong@mju.ac.kr");
        assertThat(result.getAccessToken()).isNull();
    }

    @Test
    void 가입하면_구글_계정_이메일로_비밀번호_없는_회원을_만들고_가입표를_지운다() {
        when(ticketStore.get("ticket")).thenReturn(ACCOUNT);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GoogleAuthDTO.LoginResult result = service.signup(signupRequest());

        assertThat(result.getStatus()).isEqualTo(GoogleAuthDTO.Status.LOGGED_IN);
        verify(memberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                member.getEmail().equals("hong@mju.ac.kr") && !member.hasPassword()
                        && member.getProfileImageUrl().equals("https://pic") && member.getNickname().equals("길동")));
        verify(ticketStore).remove("ticket");
    }

    @Test
    void 닉네임이_겹치면_가입표를_남겨_다시_시도할_수_있게_한다() {
        when(ticketStore.get("ticket")).thenReturn(ACCOUNT);
        doThrow(new DuplicateNicknameException()).when(memberQueryService).validateNicknameDuplication("길동");

        assertThatThrownBy(() -> service.signup(signupRequest())).isInstanceOf(DuplicateNicknameException.class);
        verify(ticketStore, never()).remove(anyString());
    }

    private static GoogleAuthDTO.SignupRequest signupRequest() {
        return GoogleAuthDTO.SignupRequest.builder()
                .signupToken("ticket").nickname("길동").college(College.values()[0])
                .studentNumber("60201234").privacyAgreed(true).build();
    }
}
