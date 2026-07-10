package nova.mjs.domain.thingo.report.service;

import nova.mjs.domain.thingo.report.dto.ReportDTO;

public interface ReportService {

    /**
     * 신고 접수: DB 기록 + (커밋 후) 관리자 메일 발송.
     *
     * @param reporterEmail 로그인 사용자 이메일
     */
    ReportDTO.Response.Detail createReport(String reporterEmail, ReportDTO.Request.Create request);
}
