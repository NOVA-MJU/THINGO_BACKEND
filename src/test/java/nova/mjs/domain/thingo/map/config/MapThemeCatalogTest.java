package nova.mjs.domain.thingo.map.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.map.exception.MapThemeNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapThemeCatalogTest {

    private final MapThemeCatalog catalog = new MapThemeCatalog(new ObjectMapper());

    @Test
    void loadsFiveThemesInDisplayOrderWithPlaces() {
        assertThat(catalog.themes()).extracting(MapThemeCatalog.Theme::id)
                .containsExactly("budget-meal", "work-cafe", "campus-walk", "senior-cafe", "group-dining");
        assertThat(catalog.themes()).allSatisfy(theme -> {
            assertThat(theme.title()).isNotBlank();
            assertThat(theme.subtitle()).isNotBlank();
            assertThat(theme.imageUrl()).startsWith("https://");
            assertThat(theme.placeCodes()).isNotEmpty();
            assertThat(new HashSet<>(theme.placeCodes())).hasSameSizeAs(theme.placeCodes());
        });
    }

    @Test
    void throwsWhenThemeIsUnknown() {
        assertThatThrownBy(() -> catalog.resolve("unknown")).isInstanceOf(MapThemeNotFoundException.class);
    }
}
