package nova.mjs.domain.thingo.block.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 차단 도메인 공통 예외.
 */
public abstract class BlockException extends BusinessBaseException {

    protected BlockException(ErrorCode errorCode) {
        super(errorCode);
    }
}
