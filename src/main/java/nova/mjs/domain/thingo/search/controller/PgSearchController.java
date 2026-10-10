package nova.mjs.domain.thingo.search.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.search.dto.SearchResponseDTO;
import nova.mjs.domain.thingo.search.exception.SearchSyncException;
import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.search.service.PgSearchIndexSyncService;
import nova.mjs.domain.thingo.search.service.PgUnifiedSearchService;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * PostgreSQL 기반 통합 검색 API.
 *
 * - Elasticsearch 제거 후 이 컨트롤러가 /api/v1/search 를 담당한다(요청/응답 스키마 동일).
 * - 색인 관리 POST(/sync, /sync/academic-guides, /rebuild-vectors)는 개발자 전용이다. 전체 재구축은 색인을
 *   비웠다가 다시 채워 그동안 검색 결과가 비므로, 배너·명지도 동기화와 같이 공유 시크릿(X-Sync-Token)으로 막는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
@Log4j2
public class PgSearchController {

    private final PgUnifiedSearchService unifiedSearchService;
    private final PgSearchIndexSyncService syncService;
    private final RealtimeKeywordService realtimeKeywordService;

    // application.yml의 app.sync.search-token으로 주입 (미설정 시 기본값 - prod에서는 반드시 실제 값으로 덮어쓸 것)
    @Value("${app.sync.search-token:change-me-search-sync-token}")
    private String searchSyncToken;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<String>> sync(
            @RequestHeader(value = "X-Sync-Token", required = false) String token
    ) {
        validateToken(token);
        syncService.syncAll();
        return ResponseEntity.ok(ApiResponse.success("Success Indexing"));
    }

    @PostMapping("/sync/academic-guides")
    public ResponseEntity<ApiResponse<String>> syncAcademicGuides(
            @RequestHeader(value = "X-Sync-Token", required = false) String token
    ) {
        validateToken(token);
        syncService.syncAcademicGuides();
        return ResponseEntity.ok(ApiResponse.success("Academic guides indexed"));
    }

    /**
     * search_vector 만 재생성하는 경량 복구 엔드포인트.
     * 트리거 누락으로 search_vector 가 비어 키워드 검색이 0 이 될 때 즉시 복구용(truncate 없음).
     */
    @PostMapping("/rebuild-vectors")
    public ResponseEntity<ApiResponse<String>> rebuildVectors(
            @RequestHeader(value = "X-Sync-Token", required = false) String token
    ) {
        validateToken(token);
        syncService.rebuildVectorsOnly();
        return ResponseEntity.ok(ApiResponse.success("Rebuilt"));
    }

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<Page<SearchResponseDTO>>> searchDetail(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(name = "order", defaultValue = "relevance") String order,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<SearchResponseDTO> result =
                unifiedSearchService.search(keyword, type, category, order, pageable);

        // 빈/공백 keyword 는 실시간 인기검색어 ZSET 오염 방지 위해 기록하지 않는다.
        if (keyword != null && !keyword.isBlank()) {
            realtimeKeywordService.recordSearch(keyword.trim());
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // 상수 시간 비교, 토큰 값은 로그에 남기지 않음 (BannerSyncController·MapSyncController와 같은 방식)
    private void validateToken(String token) {
        if (token == null || !constantTimeEquals(token, searchSyncToken)) {
            log.warn("[검색 색인 관리] 유효하지 않은 토큰으로 요청이 차단되었습니다.");
            throw new SearchSyncException(ErrorCode.SEARCH_SYNC_UNAUTHORIZED);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
