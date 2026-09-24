package nova.mjs.domain.thingo.search.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.search.dto.SearchResponseDTO;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.MapSearchRouteResolver;
import nova.mjs.domain.thingo.search.model.SearchType;
import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
    private static final int ACADEMIC_SOURCE_CONTENT_LIMIT = 6_000;
    private static final Pattern HOT_KEYWORD_SAFE = Pattern.compile("^[\\p{IsHangul}A-Za-z0-9]{2,20}$");

    private final UnifiedSearchIndexRepository repository;
    private final PinRepository pinRepository;
    private final RealtimeKeywordService realtimeKeywordService;

    public Page<SearchResponseDTO> search(String keyword,
                                          String type,
                                          String category,
                                          String order,
                                          Pageable pageable) {

        String normalizedType = normalizeType(type);
        String normalizedCategory = normalizeCategory(category);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        // S1353처럼 실제 호실 코드를 정확히 입력하면 게시물 인덱스를 거치지 않고
        // 해당 층 안내도 결과를 최우선으로 반환한다.
        if (normalizedType == null) {
            Pin exactIndoorPin = findExactIndoorPin(normalizedKeyword, normalizedCategory);
            if (exactIndoorPin != null) {
                List<SearchResponseDTO> content = pageable.getPageNumber() == 0
                        ? List.of(toMapResponse(exactIndoorPin))
                        : List.of();
                return new PageImpl<>(content, pageable, 1L);
            }
        }
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
                .content(academicSourceContent(r))
                .date(r.date())
                .link(r.link())
                .category(r.category())
                .type(r.type() == null ? null : r.type().toLowerCase())
                .imageUrl(r.imageUrl())
                .score(r.score() == null ? 0f : r.score().floatValue())
                .authorName(r.authorName())
                .likeCount(r.likeCount())
                .commentCount(r.commentCount())
                .topicIds(r.topicIds())
                .directTopicIds(r.directTopicIds())
                .build();
    }

    private Pin findExactIndoorPin(String keyword, String category) {
        String indoorCode = normalizeIndoorCode(keyword);
        if (indoorCode.isEmpty()) {
            return null;
        }
        return pinRepository.findByIndoorCodeIgnoreCase(indoorCode)
                .filter(MapSearchRouteResolver::isFloorMapTarget)
                .filter(pin -> category == null || category.equals(pin.getCategory().getCode()))
                .orElse(null);
    }

    private SearchResponseDTO toMapResponse(Pin pin) {
        String location = pin.getParentBuilding().getName() + " " + pin.getFloor().getLabel();
        return SearchResponseDTO.builder()
                .id("MAP:" + pin.getId())
                .highlightedTitle("<em>" + pin.getIndoorCode() + "</em>"
                        + (pin.getIndoorCode().equalsIgnoreCase(pin.getName()) ? "" : " · " + pin.getName()))
                .highlightedContent(location + " 층별 안내도")
                .link(MapSearchRouteResolver.link(pin))
                .category(pin.getCategory().getCode())
                .type("map")
                .score(100.0f)
                .build();
    }

    private String normalizeIndoorCode(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String academicSourceContent(SearchResultRow row) {
        if (!"ACADEMIC_GUIDE".equalsIgnoreCase(row.type()) || row.content() == null) {
            return null;
        }
        return row.content().length() <= ACADEMIC_SOURCE_CONTENT_LIMIT
                ? row.content()
                : row.content().substring(0, ACADEMIC_SOURCE_CONTENT_LIMIT);
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
