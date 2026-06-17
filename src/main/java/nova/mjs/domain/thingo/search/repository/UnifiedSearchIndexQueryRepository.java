package nova.mjs.domain.thingo.search.repository;

import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UnifiedSearchIndexQueryRepository {

    /**
     * @param hotPattern realtime 인기 키워드 OR-regex (예: "취업|장학|등록"). null/blank 이면 미적용.
     * @param hotBoost   hotPattern 매칭 시 score 가산량
     */
    Page<SearchResultRow> search(String keyword,
                                 String type,
                                 String category,
                                 String order,
                                 String hotPattern,
                                 double hotBoost,
                                 Pageable pageable);

    List<String> suggest(String keyword, int limit);
}
