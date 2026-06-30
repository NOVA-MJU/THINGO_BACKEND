package nova.mjs.domain.thingo.search.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.search.dto.SearchResponseDTO;
import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.search.service.PgSearchIndexSyncService;
import nova.mjs.domain.thingo.search.service.PgUnifiedSearchService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PostgreSQL 기반 통합 검색 API.
 *
 * - Elasticsearch 제거 후 이 컨트롤러가 /api/v1/search 를 담당한다(요청/응답 스키마 동일).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class PgSearchController {

    private final PgUnifiedSearchService unifiedSearchService;
    private final PgSearchIndexSyncService syncService;
    private final RealtimeKeywordService realtimeKeywordService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<String>> sync() {
        syncService.syncAll();
        return ResponseEntity.ok(ApiResponse.success("Success Indexing"));
    }

    /**
     * search_vector 만 재생성하는 경량 복구 엔드포인트.
     * 트리거 누락으로 search_vector 가 비어 키워드 검색이 0 이 될 때 즉시 복구용(truncate 없음).
     */
    @PostMapping("/rebuild-vectors")
    public ResponseEntity<ApiResponse<String>> rebuildVectors() {
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
}
