package nova.mjs.domain.thingo.ElasticSearch.Service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.ElasticSearch.Repository.PostgresUnifiedSearchRepository;
import nova.mjs.domain.thingo.ElasticSearch.SearchResponseDTO;
import nova.mjs.domain.thingo.ElasticSearch.SearchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnifiedSearchService {

    private final PostgresUnifiedSearchRepository postgresUnifiedSearchRepository;

    public Page<SearchResponseDTO> search(
            String keyword,
            String type,
            String category,
            String order,
            Pageable pageable
    ) {
        String normalizedType = normalizeType(type);
        String normalizedCategory = normalizeCategory(category);

        return postgresUnifiedSearchRepository.search(
                keyword,
                normalizedType,
                normalizedCategory,
                order,
                pageable
        );
    }

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        SearchType parsed = SearchType.from(rawType);
        return parsed == null ? null : parsed.name();
    }

    private String normalizeCategory(String rawCategory) {
        if (rawCategory == null) {
            return null;
        }

        String normalized = rawCategory.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
