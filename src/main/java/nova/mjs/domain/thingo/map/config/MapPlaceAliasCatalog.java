package nova.mjs.domain.thingo.map.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 학생들이 부르는 장소 이름 → 명지도에 등록된 이름.
 *
 * 핀 이름은 공식 명칭(보건의료센터, 방목학술정보관)이라 '보건실', '도서관'처럼 부르면 이름 검색에 걸리지 않거나
 * 엉뚱한 곳(학생복지실)이 오타 허용으로 잡힌다. 검색어 전체가 별칭과 같을 때만 등록 이름(또는 카테고리 라벨)으로 바꿔 검색한다.
 */
@Component
public class MapPlaceAliasCatalog {

    private static final String RESOURCE_PATH = "map/place_aliases.json";

    private final MapSearchMatcher matcher;
    private final Map<String, String> targetsByAlias;

    public MapPlaceAliasCatalog(ObjectMapper objectMapper, MapSearchMatcher matcher) {
        this.matcher = matcher;
        this.targetsByAlias = load(objectMapper);
    }

    /** 검색어가 별칭이면 등록된 이름, 아니면 원래 검색어 */
    public String resolve(String keyword) {
        if (keyword == null) {
            return null;
        }
        return targetsByAlias.getOrDefault(matcher.normalize(keyword), keyword);
    }

    private Map<String, String> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            Map<String, String> normalized = new HashMap<>();
            objectMapper.readValue(input, CatalogFile.class).aliases()
                    .forEach((alias, target) -> normalized.put(matcher.normalize(alias), target));
            return Map.copyOf(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load map place aliases: " + RESOURCE_PATH, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogFile(Map<String, String> aliases) {
    }
}
