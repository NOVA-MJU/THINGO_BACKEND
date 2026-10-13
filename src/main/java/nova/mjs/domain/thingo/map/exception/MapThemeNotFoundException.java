package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 요청한 테마지도 id를 찾을 수 없을 때 발생.
 */
public class MapThemeNotFoundException extends BusinessBaseException {

    public MapThemeNotFoundException() {
        super(ErrorCode.MAP_THEME_NOT_FOUND);
    }
}
