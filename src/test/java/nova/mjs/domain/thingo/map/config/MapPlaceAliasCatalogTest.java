package nova.mjs.domain.thingo.map.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapPlaceAliasCatalogTest {

    private final MapPlaceAliasCatalog catalog = new MapPlaceAliasCatalog(new ObjectMapper(), new MapSearchMatcher());

    @Test
    void resolvesStudentWordsToRegisteredPlaceNames() {
        assertThat(catalog.resolve("보건실")).isEqualTo("보건의료센터");
        assertThat(catalog.resolve(" 복사실 ")).isEqualTo("프린트");
        assertThat(catalog.resolve("도서관")).isEqualTo("방목학술정보관");
    }

    @Test
    void keepsKeywordWhenNotAnAlias() {
        assertThat(catalog.resolve("학생지원팀")).isEqualTo("학생지원팀");
        assertThat(catalog.resolve("보건실 위치")).isEqualTo("보건실 위치");
    }
}
