package nova.mjs.domain.thingo.report.repository;

import nova.mjs.domain.thingo.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
}
