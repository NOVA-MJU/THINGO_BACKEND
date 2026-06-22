package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 요청한 핀(건물/장소)을 찾을 수 없을 때 발생.
 */
public class PinNotFoundException extends BusinessBaseException {

    public PinNotFoundException() {
        super(ErrorCode.MAP_PIN_NOT_FOUND);
    }

    public PinNotFoundException(String message) {
        super(message, ErrorCode.MAP_PIN_NOT_FOUND);
    }
}
