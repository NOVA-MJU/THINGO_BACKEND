package nova.mjs.domain.thingo.member.google;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

public class GoogleAuthException extends BusinessBaseException {

    public GoogleAuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GoogleAuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
