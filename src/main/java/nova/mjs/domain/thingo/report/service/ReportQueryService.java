package nova.mjs.domain.thingo.report.service;

import nova.mjs.domain.thingo.report.entity.ReportTargetType;

import java.util.Set;
import java.util.UUID;

/**
 * 타 도메인(커뮤니티/댓글 등)이 "신고자 본인 자가 숨김(L1.5)" 처리를 위해 의존하는 조회 인터페이스.
 *
 * 도메인 간 직접 결합을 피하기 위해 Entity가 아닌 targetUuid 집합만 노출한다.
 */
public interface ReportQueryService {

    /**
     * 신고자 본인 화면에서 즉시 숨겨야 할 대상 targetUuid 집합.
     * (서로 다른 신고자 수가 L2 임계에 도달하기 전이라도, 신고한 본인에게는 즉시 숨긴다)
     *
     * @param reporterMemberId 로그인 사용자 member id. null이면 빈 집합.
     */
    Set<UUID> getSelfReportedTargetUuids(Long reporterMemberId, ReportTargetType targetType);
}
