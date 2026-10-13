package nova.mjs.domain.thingo.member.google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.util.jwt.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleIdTokenService googleIdTokenService;
    private final GoogleSignupTicketStore ticketStore;
    private final MemberRepository memberRepository;
    private final MemberQueryService memberQueryService;
    private final JwtUtil jwtUtil;

    /**
     * 같은 @mju.ac.kr 이메일의 회원이 있으면 이메일로 가입한 회원이어도 그대로 로그인시킨다.
     * 학교가 관리하는 Workspace 계정이라 이메일 인증을 거친 것과 같다고 본다
     */
    @Transactional(readOnly = true)
    public GoogleAuthDTO.LoginResult login(String idToken) {
        GoogleAccount account = googleIdTokenService.verify(idToken);
        return memberRepository.findByEmail(account.email())
                .map(this::loggedIn)
                .orElseGet(() -> GoogleAuthDTO.LoginResult.builder()
                        .status(GoogleAuthDTO.Status.SIGNUP_REQUIRED)
                        .signupToken(ticketStore.issue(account))
                        .email(account.email())
                        .name(account.name())
                        .profileImageUrl(account.pictureUrl())
                        .build());
    }

    @Transactional
    public GoogleAuthDTO.LoginResult signup(GoogleAuthDTO.SignupRequest request) {
        GoogleAccount account = ticketStore.get(request.getSignupToken());

        memberQueryService.validateNicknameDuplication(request.getNickname());
        memberQueryService.validateEmailDuplication(account.email());
        memberQueryService.validateStudentNumberDuplication(request.getStudentNumber());

        Member member = memberRepository.save(Member.createWithGoogle(account, request));
        ticketStore.remove(request.getSignupToken());

        // 개인정보 처리방침 동의 감사 로그 (이메일 가입과 같은 형식)
        log.info("[회원가입][개인정보 동의] uuid={}, privacyAgreed={}, agreedAt={}, provider=GOOGLE",
                member.getUuid(), request.getPrivacyAgreed(), LocalDateTime.now());
        return loggedIn(member);
    }

    private GoogleAuthDTO.LoginResult loggedIn(Member member) {
        UUID uuid = member.getUuid();
        String role = String.valueOf(member.getRole());
        return GoogleAuthDTO.LoginResult.builder()
                .status(GoogleAuthDTO.Status.LOGGED_IN)
                .accessToken(jwtUtil.generateAccessToken(uuid, member.getEmail(), role))
                .refreshToken(jwtUtil.generateRefreshToken(uuid, member.getEmail(), role))
                .build();
    }
}
