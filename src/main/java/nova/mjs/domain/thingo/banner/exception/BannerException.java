package nova.mjs.domain.thingo.banner.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

public class BannerException extends BusinessBaseException {

    public BannerException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BannerException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
