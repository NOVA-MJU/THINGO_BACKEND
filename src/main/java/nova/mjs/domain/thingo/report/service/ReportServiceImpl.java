package nova.mjs.domain.thingo.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.report.dto.ReportDTO;
import nova.mjs.domain.thingo.report.entity.Report;
import nova.mjs.domain.thingo.report.entity.ReportReason;
import nova.mjs.domain.thingo.report.event.ReportCreatedEvent;
import nova.mjs.domain.thingo.report.exception.InvalidReportDetailException;
import nova.mjs.domain.thingo.report.repository.ReportRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 신고 접수 처리.
     *
     * 1. 신고자(로그인 사용자) 조회
     * 2. 기타 사유 선택 시 상세 내용 필수 검증
     * 3. 신고 이력 DB 저장
     * 4. 커밋 이후 관리자 메일 발송을 위해 이벤트 발행
     */
    @Override
    @Transactional
    public ReportDTO.Response.Detail createReport(String reporterEmail, ReportDTO.Request.Create request) {
        log.info("신고 접수 요청 시작 - reporter: {}, targetType: {}, reason: {}",
                reporterEmail, request.getTargetType(), request.getReason());

        // 1. 신고자 조회
        Member reporter = memberRepository.findByEmail(reporterEmail)
                .orElseThrow(MemberNotFoundException::new);

        // 2. 기타 사유 상세 검증 (기타 선택 시 내용 필수)
        String etcDetail = normalizeEtcDetail(request.getReason(), request.getEtcDetail());

        // 3. 신고 이력 저장
        Report report = reportRepository.save(
                Report.of(reporter, request.getTargetType(), request.getTargetUuid(), request.getReason(), etcDetail)
        );

        // 4. 커밋 이후 관리자 메일 발송(외부 I/O는 트랜잭션 밖에서 수행)
        eventPublisher.publishEvent(ReportCreatedEvent.of(report, reporter));

        log.info("신고 접수 완료 - reportUuid: {}, reporter: {}", report.getUuid(), reporterEmail);
        return ReportDTO.Response.Detail.from(report);
    }

    /**
     * 기타 사유가 아니면 상세 내용을 버리고, 기타 사유면 공백이 아닌 내용을 강제한다.
     */
    private String normalizeEtcDetail(ReportReason reason, String rawEtcDetail) {
        if (reason != ReportReason.ETC) {
            return null;
        }
        if (rawEtcDetail == null || rawEtcDetail.isBlank()) {
            throw new InvalidReportDetailException();
        }
        return rawEtcDetail.trim();
    }
}
