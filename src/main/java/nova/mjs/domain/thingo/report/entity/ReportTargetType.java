package nova.mjs.domain.thingo.report.entity;

import lombok.Getter;

/**
 * 신고 대상 유형.
 *
 * 신고는 대상 엔티티를 직접 참조하지 않고 (유형 + UUID)만 기록한다.
 * REVIEW(명지도 리뷰)는 백엔드 미구현 상태이나, 프론트 확장을 위해 값만 미리 둔다.
 */
@Getter
public enum ReportTargetType {

    BOARD("게시판 글"),
    COMMENT("댓글"),
    REVIEW("명지도 리뷰");

    private final String label;

    ReportTargetType(String label) {
        this.label = label;
    }
}
