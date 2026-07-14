package nova.mjs.domain.thingo.report.repository;

import nova.mjs.domain.thingo.report.entity.Report;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 특정 대상(유형 + UUID)을 신고한 "서로 다른 신고자" 수.
     *
     * - 한 사용자가 같은 대상을 여러 번 신고해도 1명으로 센다(임계 조작 방지).
     * - L2 자동 숨김 임계 판단에 사용한다.
     */
    @Query("""
        select count(distinct r.reporter.id)
        from Report r
        where r.targetType = :targetType
          and r.targetUuid = :targetUuid
    """)
    long countDistinctReportersByTarget(
            @Param("targetType") ReportTargetType targetType,
            @Param("targetUuid") UUID targetUuid
    );

    /**
     * 특정 대상(유형 + UUID)에 접수된 누적 신고 건수(중복 신고 포함).
     *
     * - 방금 저장한 신고까지 포함해 "이번이 몇 번째 신고인지"를 계산하는 데 쓴다.
     * - 관리자 메일에 신고 누적 정보를 표기하기 위한 값이다.
     */
    long countByTargetTypeAndTargetUuid(ReportTargetType targetType, UUID targetUuid);

    /**
     * 특정 사용자가 신고한 대상(유형별) UUID 목록.
     *
     * - 신고자 본인 화면 즉시 자가 숨김(L1.5)에 사용한다(임계 도달 전이라도 본인에게는 숨김).
     */
    @Query("""
        select distinct r.targetUuid
        from Report r
        where r.reporter.id = :reporterId
          and r.targetType = :targetType
    """)
    List<UUID> findTargetUuidsByReporterIdAndTargetType(
            @Param("reporterId") Long reporterId,
            @Param("targetType") ReportTargetType targetType
    );
}
