package nova.mjs.domain.thingo.report.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 신고 도메인 공통 예외.
 */
public abstract class ReportException extends BusinessBaseException {

    protected ReportException(ErrorCode errorCode) {
        super(errorCode);
    }
}
