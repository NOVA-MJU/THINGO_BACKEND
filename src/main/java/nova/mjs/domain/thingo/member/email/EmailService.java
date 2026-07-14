package nova.mjs.domain.thingo.member.email;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.member.exception.EmailIsInvalidException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redis;

    private static final String CODE_PREFIX     = "email:verify:";
    private static final String VERIFIED_PREFIX = "recovery:verified:";
    private static final Duration CODE_TTL      = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL  = Duration.ofMinutes(15);

    private static final String TEMPLATE_PATH = "templates/email/verification-code.html";
    private static final String IMAGE_DIR = "templates/email/images/";
    private static final String CODE_PLACEHOLDER = "{{CODE}}";
    private static final String MAIL_SUBJECT = "[Thingo] 회원가입 인증코드";

    // 메일 본문에 CID로 인라인 삽입할 이미지 (contentId -> 파일명).
    // 외부 호스팅 없이 메일 자체에 첨부되어 모든 메일 클라이언트에서 표시된다.
    private static final Map<String, String> INLINE_IMAGES = Map.of(
            "thLogo",        "th_logo.png",
            "thingoLogo",    "thingo_logo.png",
            "instagramIcon", "instagram.png"
    );

    // 템플릿은 변하지 않으므로 최초 1회만 읽어 캐시
    private volatile String cachedTemplate;

    /** 인증코드 발송 (HTML 템플릿) */
    public String sendVerificationEmail(String rawEmail) {
        final String email = normalize(rawEmail);
        final String code  = generateVerificationCode();

        // 1) 인증코드를 Redis에 TTL과 함께 저장
        redis.opsForValue().set(codeKey(email), code, CODE_TTL);

        // 2) 템플릿에 인증코드를 주입해 HTML 본문 생성
        final String html = loadTemplate().replace(CODE_PLACEHOLDER, code);

        // 3) HTML 메일 발송 (이미지는 CID 인라인 첨부)
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true: 본문 + 인라인 이미지 첨부를 위해 멀티파트로 구성
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(email);
            helper.setSubject(MAIL_SUBJECT);
            helper.setText(html, true); // true = HTML (반드시 인라인 추가보다 먼저 호출)
            attachInlineImages(helper);
            mailSender.send(message);
        } catch (org.springframework.mail.MailSendException e) {
            // SMTP 서버가 수신자 주소를 즉시 거부(SendFailedException)한 경우 = 실존하지 않는 이메일
            if (isRecipientRejected(e)) {
                log.warn("인증 메일 수신자 거부 - email: {}", maskEmail(email));
                throw new EmailIsInvalidException();
            }
            log.error("인증 메일 발송 실패 - email: {}", maskEmail(email), e);
            throw new IllegalStateException("인증 메일 발송에 실패했습니다.", e);
        } catch (MessagingException e) {
            log.error("인증 메일 발송 실패 - email: {}", maskEmail(email), e);
            throw new IllegalStateException("인증 메일 발송에 실패했습니다.", e);
        }

        return "인증 코드가 이메일로 발송되었습니다.";
    }

    /** 템플릿이 cid:로 참조하는 이미지를 인라인 첨부. 파일이 없으면 건너뛴다(메일은 정상 발송). */
    private void attachInlineImages(MimeMessageHelper helper) throws MessagingException {
        for (Map.Entry<String, String> image : INLINE_IMAGES.entrySet()) {
            ClassPathResource resource = new ClassPathResource(IMAGE_DIR + image.getValue());
            if (!resource.exists()) {
                log.warn("인증 메일 인라인 이미지 누락 - {} (cid={})", image.getValue(), image.getKey());
                continue;
            }
            helper.addInline(image.getKey(), resource);
        }
    }

    /** MailSendException 원인이 SendFailedException(수신자 주소 거부)인지 확인 */
    private boolean isRecipientRejected(org.springframework.mail.MailSendException e) {
        if (e.getCause() instanceof SendFailedException) {
            return true;
        }
        return e.getFailedMessages().values().stream().anyMatch(cause -> cause instanceof SendFailedException);
    }

    /** 인증 메일 HTML 템플릿 로드(최초 1회 캐시) */
    private String loadTemplate() {
        if (cachedTemplate == null) {
            synchronized (this) {
                if (cachedTemplate == null) {
                    try (InputStream is = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
                        cachedTemplate = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException("인증 메일 템플릿 로드 실패: " + TEMPLATE_PATH, e);
                    }
                }
            }
        }
        return cachedTemplate;
    }

    /** 로그용 이메일 마스킹 (앞 2글자만 노출) */
    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 2) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.substring(0, 2) + "***" + email.substring(at);
    }

    /** 인증코드 검증(성공 시 소각) */
    public EmailVerificationResultDto verifyEmailCode(String rawEmail, String code) {
        final String email  = normalize(rawEmail);
        final String stored = redis.opsForValue().get(codeKey(email));

        final boolean matched = (stored != null && stored.equals(code));
        if (matched) {
            redis.delete(codeKey(email));
        }
        return new EmailVerificationResultDto(email, matched);
    }

    /** 비번찾기용: 인증 성공 플래그 세팅(서버 내부 상태) */
    public void markVerifiedForRecovery(String rawEmail) {
        final String email = normalize(rawEmail);
        redis.opsForValue().set(verifiedKey(email), "1", VERIFIED_TTL);
    }

    /** 비번찾기용: 인증 성공 플래그 확인 */
    public boolean hasVerifiedForRecovery(String rawEmail) {
        final String email = normalize(rawEmail);
        Boolean exists = redis.hasKey(verifiedKey(email));
        return exists != null && exists;
    }

    /** 비번찾기용: 인증 성공 플래그 소각 */
    public void clearVerifiedForRecovery(String rawEmail) {
        final String email = normalize(rawEmail);
        redis.delete(verifiedKey(email));
    }

    // ===== 내부 유틸 =====
    private String codeKey(String email)     { return CODE_PREFIX + email; }
    private String verifiedKey(String email) { return VERIFIED_PREFIX + email; }
    private String normalize(String email)   { return email == null ? null : email.trim().toLowerCase(); }
    private String generateVerificationCode(){ return UUID.randomUUID().toString().substring(0, 6).toUpperCase(); }
}
