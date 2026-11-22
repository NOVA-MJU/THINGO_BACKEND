package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 네이버 지도 글자로 장소를 만들 수 없을 때 발생 (대동명지도 가게 종류가 아니거나 학교에서 너무 먼 곳).
 */
public class SymbolPlaceInvalidException extends BusinessBaseException {

    public SymbolPlaceInvalidException(String message) {
        super(message, ErrorCode.MAP_SYMBOL_PLACE_INVALID);
    }
}
