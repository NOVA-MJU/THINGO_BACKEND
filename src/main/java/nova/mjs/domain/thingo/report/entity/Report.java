package nova.mjs.domain.thingo.report.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

import java.util.UUID;

/**
 * 신고 접수 기록.
 *
 * - 신고는 메일 발송과 별개로 DB에도 남겨 관리자 추후 조치 근거로 삼는다.
 * - 대상(게시글/댓글/리뷰)은 FK로 묶지 않고 (유형 + UUID)로만 저장한다.
 *   (도메인 간 결합을 피하고, 대상이 삭제돼도 신고 이력은 보존)
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "report")
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    /** 신고자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportTargetType targetType;

    /** 신고 대상 식별자(게시글/댓글/리뷰의 UUID) */
    @Column(nullable = false)
    private UUID targetUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    /** 기타 사유 상세 (reason=ETC일 때만 채워짐, 최대 400자) */
    @Column(length = 400)
    private String etcDetail;

    /**
     * 신고 생성.
     *
     * @param etcDetail 기타 사유 상세. 기타 사유가 아니면 null.
     */
    public static Report of(Member reporter,
                            ReportTargetType targetType,
                            UUID targetUuid,
                            ReportReason reason,
                            String etcDetail) {
        return Report.builder()
                .uuid(UUID.randomUUID())
                .reporter(reporter)
                .targetType(targetType)
                .targetUuid(targetUuid)
                .reason(reason)
                .etcDetail(etcDetail)
                .build();
    }
}
