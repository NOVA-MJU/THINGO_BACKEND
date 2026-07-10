package nova.mjs.domain.thingo.report.event;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.report.entity.Report;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 신고 접수 완료 이벤트.
 *
 * 트랜잭션 커밋 이후 관리자 메일 발송 트리거로 쓰인다.
 * (외부 I/O인 메일 발송을 트랜잭션 밖으로 빼기 위해 값 스냅샷을 담는다.)
 */
@Getter
@Builder
public class ReportCreatedEvent {

    private final UUID reportUuid;
    private final String reporterName;
    private final String reporterNickname;
    private final String reporterEmail;
    private final UUID reporterUuid;

    private final String targetTypeLabel;
    private final UUID targetUuid;

    private final String reasonName;
    private final String reasonLabel;
    private final String reasonDescription;
    private final String etcDetail;

    private final LocalDateTime createdAt;

    public static ReportCreatedEvent of(Report report, Member reporter) {
        return ReportCreatedEvent.builder()
                .reportUuid(report.getUuid())
                .reporterName(reporter.getName())
                .reporterNickname(reporter.getNickname())
                .reporterEmail(reporter.getEmail())
                .reporterUuid(reporter.getUuid())
                .targetTypeLabel(report.getTargetType().getLabel())
                .targetUuid(report.getTargetUuid())
                .reasonName(report.getReason().name())
                .reasonLabel(report.getReason().getLabel())
                .reasonDescription(report.getReason().getDescription())
                .etcDetail(report.getEtcDetail())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
