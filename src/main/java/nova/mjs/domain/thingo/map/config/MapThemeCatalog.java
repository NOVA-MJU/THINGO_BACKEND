package nova.mjs.domain.thingo.map.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.map.exception.MapThemeNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 명지도 검색 화면의 테마지도 카탈로그.
 *
 * 테마는 기획이 바뀌지 않는 한 고정 콘텐츠라 DB·시트 대신 리소스 JSON(map/theme_maps.json)으로 관리한다.
 * 테마마다 장소 코드 목록을 노출 순서대로 들고, 장소 자체(이름·좌표·사진)는 명지도 핀 데이터를 그대로 쓴다.
 */
@Component
public class MapThemeCatalog {

    private static final String RESOURCE_PATH = "map/theme_maps.json";

    private final List<Theme> themes;

    public MapThemeCatalog(ObjectMapper objectMapper) {
        this.themes = load(objectMapper);
    }

    public List<Theme> themes() {
        return themes;
    }

    public Theme resolve(String themeId) {
        return themes.stream()
                .filter(theme -> theme.id().equals(themeId))
                .findFirst()
                .orElseThrow(MapThemeNotFoundException::new);
    }

    private List<Theme> load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            List<Theme> loaded = objectMapper.readValue(input, CatalogFile.class).themes();
            if (loaded == null || loaded.isEmpty()) {
                throw new IllegalStateException("Map theme catalog has no themes: " + RESOURCE_PATH);
            }
            return List.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load map theme catalog: " + RESOURCE_PATH, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CatalogFile(List<Theme> themes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Theme(String id, String title, String subtitle, String imageUrl, List<String> placeCodes) {
    }
}
