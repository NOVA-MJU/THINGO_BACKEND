package nova.mjs.domain.thingo.search.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.search.dto.SearchResponseDTO;
import nova.mjs.domain.thingo.search.model.SearchType;
import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PostgreSQL 기반 통합 검색 서비스.
 *
 * 응답 스키마는 기존 ES 의 SearchResponseDTO 를 그대로 재사용한다.
 * realtime 인기 검색어 top-K 가 결과 title 에 등장하면 score 가산.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PgUnifiedSearchService {

    private static final int REALTIME_TOP_K = 10;
    private static final double REALTIME_HOT_BOOST = 0.05d;
    private static final Pattern HOT_KEYWORD_SAFE = Pattern.compile("^[\\p{IsHangul}A-Za-z0-9]{2,20}$");

    private final UnifiedSearchIndexRepository repository;
    private final RealtimeKeywordService realtimeKeywordService;

    public Page<SearchResponseDTO> search(String keyword,
                                          String type,
                                          String category,
                                          String order,
                                          Pageable pageable) {

        String normalizedType = normalizeType(type);
        String normalizedCategory = normalizeCategory(category);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String hotPattern = buildHotPattern();

        Page<SearchResultRow> rows = repository.search(
                normalizedKeyword,
                normalizedType,
                normalizedCategory,
                order,
                hotPattern,
                REALTIME_HOT_BOOST,
                pageable
        );

        return rows.map(this::toResponse);
    }

    /**
     * realtime 인기 키워드 top-K 를 OR-regex 로 결합.
     * - regex 인젝션 방지: 한글/영문/숫자 2~20자만 허용
     */
    private String buildHotPattern() {
        List<String> top;
        try {
            top = realtimeKeywordService.getTopKeywords(REALTIME_TOP_K);
        } catch (Exception e) {
            return null;
        }
        if (top == null || top.isEmpty()) {
            return null;
        }
        String joined = top.stream()
                .filter(k -> k != null && HOT_KEYWORD_SAFE.matcher(k).matches())
                .distinct()
                .collect(Collectors.joining("|"));
        return joined.isBlank() ? null : joined;
    }

    private SearchResponseDTO toResponse(SearchResultRow r) {
        return SearchResponseDTO.builder()
                .id(r.id())
                .highlightedTitle(coalesce(r.highlightedTitle(), r.title()))
                .highlightedContent(coalesce(r.highlightedContent(), r.content()))
                .date(r.date())
                .link(r.link())
                .category(r.category())
                .type(r.type() == null ? null : r.type().toLowerCase())
                .imageUrl(r.imageUrl())
                .score(r.score() == null ? 0f : r.score().floatValue())
                .authorName(r.authorName())
                .likeCount(r.likeCount())
                .commentCount(r.commentCount())
                .build();
    }

    private String normalizeType(String rawType) {
        SearchType parsed = SearchType.from(rawType);
        return parsed == null ? null : parsed.name();
    }

    private String normalizeCategory(String rawCategory) {
        if (rawCategory == null) return null;
        String t = rawCategory.trim();
        return t.isBlank() ? null : t;
    }

    private String coalesce(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }
}
