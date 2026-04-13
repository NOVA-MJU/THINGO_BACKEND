package nova.mjs.domain.thingo.member.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.member.DTO.MemberDTO;
import nova.mjs.domain.thingo.member.email.EmailService;
import nova.mjs.domain.thingo.member.email.EmailVerificationResultDto;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.DuplicateNicknameException;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.exception.PasswordIsInvalidException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.member.service.query.MemberQueryService;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.exception.request.RequestException;
import nova.mjs.util.jwt.JwtUtil;
import nova.mjs.util.s3.S3DomainType;
import nova.mjs.util.s3.S3Service;
import nova.mjs.util.security.AuthDTO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandServiceImpl implements MemberCommandService {

    private final MemberQueryService memberQueryService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final S3Service s3Service;


    /**
     * S3에 프로필 이미지를 업로드하고 CloudFront URL 반환
     */
    @Override
    public String uploadProfileImage(MultipartFile file) {
        try {
            return s3Service.uploadFile(file, S3DomainType.PROFILE_IMAGE);
        } catch (IOException e) {
            log.error("[프로필 이미지 업로드 실패]", e);
            throw new RequestException(ErrorCode.S3_IMAGE_UPLOAD_FAILED);
        }
    }


    /**
     * 회원 가입 로직
     */
    @Override
    public AuthDTO.LoginResponseDTO registerMember(MemberDTO.MemberRegistrationRequestDTO request) {
        // 회원이 입력한 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 닉네임 중복 확인
        memberQueryService.validateNicknameDuplication(request.getNickname());
        // 이메일 도메인 확인
        memberQueryService.validateEmailDomain(request.getEmail());
        // 이메일 중복 확인
        memberQueryService.validateEmailDuplication(request.getEmail());
        // 학번 중복 확인
        memberQueryService.validateStudentNumberDuplication(request.getStudentNumber());

        // 회원객체 생성
        Member newMember = Member.create(request, encodedPassword);
        newMember = memberRepository.save(newMember);

        UUID userId = newMember.getUuid();
        String email = newMember.getEmail();
        String role = String.valueOf(newMember.getRole());// Member 엔티티에 role 필드가 있어야 함

        // Access Token & Refresh Token 생성
        String accessToken = jwtUtil.generateAccessToken(userId, email, role);
        String refreshToken = jwtUtil.generateRefreshToken(userId, email, role);

        // 응답 DTO 반환
        return AuthDTO.LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // 회원 정보 수정
    @Override
    public Member updateMember(String emailId, MemberDTO.MemberUpdateRequestDTO requestDTO) {
        Member member = memberQueryService.getMemberByEmail(emailId);
        if (requestDTO.getNickname() != null && !requestDTO.getNickname().equals(member.getNickname())
                && memberRepository.existsByNickname(requestDTO.getNickname())) {
            throw new DuplicateNicknameException();
        }
        if (requestDTO.getStudentNumber() != null && !requestDTO.getStudentNumber().equals(member.getStudentNumber())) {
            memberQueryService.validateStudentNumber(requestDTO.getStudentNumber());
        }
        member.update(requestDTO);
        return memberRepository.save(member);
    }

    // 비밀번호 변경
    @Transactional
    @Override
    public void updatePassword(String emailId, MemberDTO.PasswordRequestDTO requestDTO) {
        Member member = memberQueryService.getMemberByEmail(emailId);
        if (!passwordEncoder.matches(requestDTO.getPassword(), member.getPassword())) {
            throw new PasswordIsInvalidException(); // 기존 비밀번호가 틀린 경우 예외 발생
        }

        if (requestDTO.getNewPassword() == null || requestDTO.getNewPassword().isBlank()) {
            throw new RequestException(ErrorCode.INVALID_REQUEST); // 새 비밀번호가 비어 있는 경우 예외 발생
        }

        // 기존 비밀번호와 새 비밀번호가 동일한지 체크
        if (passwordEncoder.matches(requestDTO.getNewPassword(), member.getPassword())) {
            throw new RequestException(ErrorCode.SAME_PASSWORD_NOT_ALLOWED); // 동일한 비밀번호로 변경 불가
        }

        String encodedNewPassword = passwordEncoder.encode(requestDTO.getNewPassword());
        member.updatePassword(encodedNewPassword);
        memberRepository.save(member);
    }

    // 회원 삭제
    @Transactional
    @Override
    public void deleteMember(String emailId, MemberDTO.PasswordRequestDTO requestPassword) {
        Member member = memberQueryService.getMemberByEmail(emailId);
        // 비밀번호 검증
        boolean passwordMatches = passwordEncoder.matches(requestPassword.getPassword(), member.getPassword());

        if (!passwordMatches) {
            throw new PasswordIsInvalidException();
        }
        memberRepository.delete(member);
        log.info("회원 삭제 - emailId: {}", emailId);
    }

    /**
     * 2단계: 코드 검증 성공 시 내부 플래그 세팅 (존재/부재는 외부에 노출 금지)
     */
    @Transactional
    public void verifyCodeForRecovery(String rawEmail, String verificationCode) {
        EmailVerificationResultDto verificationResult = emailService.verifyEmailCode(rawEmail, verificationCode);
        if (!verificationResult.isMatched()) {
            throw new RequestException(ErrorCode.INVALID_REQUEST); // 코드 불일치/만료/없는 계정 모두 동일 처리
        }

        // 내부적으로만 계정 존재 확인 (없어도 같은 에러)
        memberRepository.findByEmail(normalize(rawEmail))
                .orElseThrow(() -> new RequestException(ErrorCode.INVALID_REQUEST));

        // 인증 성공 플래그(15분) 세팅
        emailService.markVerifiedForRecovery(rawEmail);
    }

    /**
     * 3단계: 내부 플래그 확인되면 비밀번호 변경, 아니면 동일 실패 응답
     */
    @Transactional
    public void resetPasswordAfterVerified(String rawEmail, String newPassword) {
        final String email = normalize(rawEmail);

        if (!emailService.hasVerifiedForRecovery(email)) {
            throw new RequestException(ErrorCode.INVALID_REQUEST); // 미인증/만료
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new); // 실제 예외는 내부에서만 사용, 외부에는 동일 코드로 응답 처리 가능

        validateNewPasswordPolicy(newPassword);

        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new RequestException(ErrorCode.SAME_PASSWORD_NOT_ALLOWED);
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        member.updatePassword(encodedNewPassword);
        memberRepository.save(member);

        // 인증 플래그 소각
        emailService.clearVerifiedForRecovery(email);
    }

    // ===== 유틸 =====
    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private void validateNewPasswordPolicy(String password) {
        if (password == null || password.isBlank()) {
            throw new RequestException(ErrorCode.INVALID_REQUEST);
        }
        if (password.length() < 8) {
            throw new RequestException(ErrorCode.PASSWORD_IS_INVALID);
        }
        // 필요 시 문자조합 규칙 추가
    }
}
