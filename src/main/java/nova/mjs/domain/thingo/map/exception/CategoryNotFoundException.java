package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 요청한 카테고리(칩) 코드를 찾을 수 없을 때 발생.
 */
public class CategoryNotFoundException extends BusinessBaseException {

    public CategoryNotFoundException() {
        super(ErrorCode.MAP_CATEGORY_NOT_FOUND);
    }

    public CategoryNotFoundException(String message) {
        super(message, ErrorCode.MAP_CATEGORY_NOT_FOUND);
    }
}
