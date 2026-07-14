package nova.mjs.domain.thingo.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.report.dto.ReportDTO;
import nova.mjs.domain.thingo.report.entity.Report;
import nova.mjs.domain.thingo.report.entity.ReportReason;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import nova.mjs.domain.thingo.report.event.ReportCreatedEvent;
import nova.mjs.domain.thingo.report.exception.InvalidReportDetailException;
import nova.mjs.domain.thingo.report.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService, ReportQueryService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    /** 콘텐츠 도메인별 자동 숨김 구현 목록. 각 구현은 자기 targetType만 처리한다(커뮤니티/리뷰 등). */
    private final List<ContentModerationPort> contentModerationPorts;

    /** 서로 다른 신고자 수가 이 값 이상이면 대상 콘텐츠를 자동 숨김한다(L2). */
    @Value("${moderation.report.hide-threshold:5}")
    private long hideThreshold;

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

        // 4. 신고 누적 집계
        //    - 누적 신고 건수(이번 포함): 관리자 메일에 "몇 번째 신고인지" 표기용
        //    - 서로 다른 신고자 수: 자동 숨김 임계 판단용
        long targetReportCount = reportRepository.countByTargetTypeAndTargetUuid(
                request.getTargetType(), request.getTargetUuid());
        long distinctReporterCount = reportRepository.countDistinctReportersByTarget(
                request.getTargetType(), request.getTargetUuid());

        // 5. 신고 누적 자동 숨김(L2): 서로 다른 신고자 수가 임계 이상이면 대상 콘텐츠를 숨긴다.
        //    (숨김은 DB 쓰기라 같은 트랜잭션에서 처리, 멱등하므로 임계 초과 이후에도 안전)
        applyAutoHideIfThresholdReached(request.getTargetType(), request.getTargetUuid(), distinctReporterCount);

        // 6. 커밋 이후 관리자 메일 발송(외부 I/O는 트랜잭션 밖에서 수행)
        eventPublisher.publishEvent(
                ReportCreatedEvent.of(report, reporter, targetReportCount, distinctReporterCount));

        log.info("신고 접수 완료 - reportUuid: {}, reporter: {}", report.getUuid(), reporterEmail);
        return ReportDTO.Response.Detail.from(report);
    }

    /**
     * 대상에 대한 서로 다른 신고자 수가 임계 이상이면 콘텐츠 도메인에 숨김을 지시한다.
     * 포트를 통해서만 호출하여 신고 도메인이 게시글/댓글 엔티티에 직접 결합되지 않게 한다.
     */
    private void applyAutoHideIfThresholdReached(ReportTargetType targetType,
                                                 java.util.UUID targetUuid,
                                                 long distinctReporters) {
        if (distinctReporters >= hideThreshold) {
            log.info("신고 임계 도달 자동 숨김 - targetType: {}, targetUuid: {}, 신고자수: {} (임계: {})",
                    targetType, targetUuid, distinctReporters, hideThreshold);
            // 등록된 모든 콘텐츠 숨김 포트에 위임(각 구현이 자기 타입만 처리)
            contentModerationPorts.forEach(port -> port.hideByReport(targetType, targetUuid));
        }
    }

    /**
     * 신고자 본인 자가 숨김(L1.5) 대상 targetUuid 집합.
     * L2 임계 도달 전이라도, 내가 신고한 대상은 내 화면에서 즉시 숨긴다.
     */
    @Override
    public Set<UUID> getSelfReportedTargetUuids(Long reporterMemberId, ReportTargetType targetType) {
        if (reporterMemberId == null) {
            return Set.of();
        }
        return new HashSet<>(
                reportRepository.findTargetUuidsByReporterIdAndTargetType(reporterMemberId, targetType));
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
