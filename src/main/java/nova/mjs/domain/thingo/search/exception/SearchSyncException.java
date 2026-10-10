package nova.mjs.domain.thingo.search.exception;

import nova.mjs.util.exception.BusinessBaseException;
import nova.mjs.util.exception.ErrorCode;

/**
 * 검색 색인 관리(전체 재구축·학사안내문 색인·벡터 재생성) 요청의 예외.
 * - 토큰 불일치: SEARCH_SYNC_UNAUTHORIZED
 */
public class SearchSyncException extends BusinessBaseException {

    public SearchSyncException(ErrorCode errorCode) {
        super(errorCode);
    }
}
