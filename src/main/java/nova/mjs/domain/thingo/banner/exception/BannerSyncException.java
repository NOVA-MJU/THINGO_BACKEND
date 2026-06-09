package nova.mjs.domain.thingo.banner.exception;

import nova.mjs.util.exception.ErrorCode;

/**
 * 배너 동기화(구글 시트 → DB) 과정의 예외.
 * - 토큰 불일치: BANNER_SYNC_UNAUTHORIZED
 * - 행 검증 실패: BANNER_SYNC_INVALID_ROW (어떤 행이 왜 실패했는지 메시지로 전달)
 */
public class BannerSyncException extends BannerException {

    public BannerSyncException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BannerSyncException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}
