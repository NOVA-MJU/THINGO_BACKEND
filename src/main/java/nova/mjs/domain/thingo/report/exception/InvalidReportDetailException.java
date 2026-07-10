package nova.mjs.domain.thingo.report.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 기타 사유 선택 시 상세 내용이 비어 있을 때 발생.
 */
public class InvalidReportDetailException extends ReportException {

    public InvalidReportDetailException() {
        super(ErrorCode.REPORT_REASON_DETAIL_REQUIRED);
    }
}
