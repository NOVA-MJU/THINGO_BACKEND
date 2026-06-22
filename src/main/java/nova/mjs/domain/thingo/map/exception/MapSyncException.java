package nova.mjs.domain.thingo.map.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 명지도 구글 시트 동기화 관련 예외.
 * - 토큰 검증 실패: MAP_SYNC_UNAUTHORIZED
 * - 행 데이터 오류: MAP_SYNC_INVALID_ROW (어느 행이 왜 틀렸는지 메시지로 명시)
 */
public class MapSyncException extends BusinessBaseException {

    public MapSyncException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MapSyncException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
