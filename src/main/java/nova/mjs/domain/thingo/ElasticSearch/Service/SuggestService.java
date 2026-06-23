package nova.mjs.domain.thingo.ElasticSearch.Service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.ElasticSearch.Repository.PostgresUnifiedSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestService {

    private static final int DEFAULT_SIZE = 7;

    private final PostgresUnifiedSearchRepository postgresUnifiedSearchRepository;

    public List<String> getSuggestions(String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        if (keyword.isBlank()) {
            return List.of();
        }

        return postgresUnifiedSearchRepository.autocomplete(keyword, null, DEFAULT_SIZE);
    }
}
