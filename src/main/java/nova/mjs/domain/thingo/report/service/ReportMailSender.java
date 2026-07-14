package nova.mjs.domain.thingo.report.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.report.event.ReportCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * 신고 접수 시 관리자에게 메일을 발송한다.
 *
 * - 신고 트랜잭션이 커밋된 뒤에만 발송(AFTER_COMMIT)하여 외부 I/O를 트랜잭션 밖으로 뺀다.
 * - 발송 실패는 로그만 남기고 삼킨다. (신고 이력은 이미 DB에 저장됨)
 */
@Slf4j
@Component
public class ReportMailSender {

    private final JavaMailSender mailSender;
    private final String recipientEmail;
    private final String senderEmail;

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReportMailSender(
            JavaMailSender mailSender,
            @Value("${app.report.recipient-email:mjsearch2025@gmail.com}") String recipientEmail,
            @Value("${spring.mail.username:mjsearch2025@gmail.com}") String senderEmail) {
        this.mailSender = mailSender;
        this.recipientEmail = recipientEmail;
        this.senderEmail = senderEmail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportCreated(ReportCreatedEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipientEmail);
            helper.setFrom(senderEmail);
            helper.setSubject(buildSubject(event));
            helper.setText(buildBody(event), true);

            mailSender.send(message);
            log.info("신고 접수 메일 발송 완료 - reportUuid: {}", event.getReportUuid());
        } catch (MessagingException | RuntimeException e) {
            // 메일 발송 실패해도 신고 이력은 보존되므로 요청은 실패시키지 않는다.
            log.error("신고 접수 메일 발송 실패 - reportUuid: {}", event.getReportUuid(), e);
        }
    }

    private String buildSubject(ReportCreatedEvent event) {
        // 제목에도 누적 신고 횟수를 노출해 심각도를 한눈에 파악하게 한다
        return "[MJS 신고] " + event.getTargetTypeLabel()
                + " - " + event.getReasonLabel()
                + " (누적 " + event.getTargetReportCount() + "회)";
    }

    private String buildBody(ReportCreatedEvent event) {
        String etc = (event.getEtcDetail() == null || event.getEtcDetail().isBlank())
                ? "-"
                : escape(event.getEtcDetail());

        return "<h2>신고가 접수되었습니다.</h2>"
                + "<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse'>"
                + row("신고 ID", event.getReportUuid().toString())
                + row("접수 시각", event.getCreatedAt() == null ? "-" : event.getCreatedAt().format(TIMESTAMP))
                + row("신고자 이름", escape(event.getReporterName()))
                + row("신고자 닉네임", escape(event.getReporterNickname()))
                + row("신고자 이메일", escape(event.getReporterEmail()))
                + row("신고자 UUID", event.getReporterUuid().toString())
                + row("대상 유형", escape(event.getTargetTypeLabel()))
                + row("대상 UUID", event.getTargetUuid().toString())
                + row("누적 신고 횟수", "이번이 " + event.getTargetReportCount() + "번째 신고 (서로 다른 신고자 "
                        + event.getDistinctReporterCount() + "명)")
                + row("신고 사유", escape(event.getReasonLabel()) + " (" + event.getReasonName() + ")")
                + row("사유 설명", escape(event.getReasonDescription()))
                + row("기타 상세", etc)
                + "</table>";
    }

    private String row(String label, String value) {
        return "<tr><th align='left'>" + label + "</th><td>" + (value == null ? "-" : value) + "</td></tr>";
    }

    /** 메일 본문에 사용자 입력을 넣을 때 최소한의 HTML 이스케이프 */
    private String escape(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
