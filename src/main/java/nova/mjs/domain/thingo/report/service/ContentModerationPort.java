package nova.mjs.domain.thingo.report.service;

import nova.mjs.domain.thingo.report.entity.ReportTargetType;

import java.util.UUID;

/**
 * 신고 도메인이 콘텐츠 도메인에 숨김을 요청하기 위한 포트 (L2).
 *
 * - 신고 도메인은 게시글/댓글 엔티티를 직접 알지 않고, 이 인터페이스로만 숨김을 지시한다.
 * - 실제 구현은 콘텐츠(커뮤니티) 도메인에서 제공한다(도메인 간 직접 결합 방지).
 */
public interface ContentModerationPort {

    /**
     * 신고 누적 임계를 초과한 대상 콘텐츠를 숨긴다.
     * - 이미 숨김이거나 대상이 없으면 아무 일도 하지 않는다(멱등).
     */
    void hideByReport(ReportTargetType targetType, UUID targetUuid);
}
