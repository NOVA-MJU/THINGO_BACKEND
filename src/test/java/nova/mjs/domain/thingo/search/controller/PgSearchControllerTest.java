package nova.mjs.domain.thingo.search.controller;

import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.search.exception.SearchSyncException;
import nova.mjs.domain.thingo.search.service.PgSearchIndexSyncService;
import nova.mjs.domain.thingo.search.service.PgUnifiedSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PgSearchControllerTest {

    private static final String VALID_TOKEN = "valid-secret-token";

    @Mock
    private PgUnifiedSearchService unifiedSearchService;
    @Mock
    private PgSearchIndexSyncService syncService;
    @Mock
    private RealtimeKeywordService realtimeKeywordService;

    private PgSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new PgSearchController(unifiedSearchService, syncService, realtimeKeywordService);
        ReflectionTestUtils.setField(controller, "searchSyncToken", VALID_TOKEN);
    }

    @Test
    @DisplayName("토큰이 일치하면 색인 관리 작업을 실행한다")
    void should_색인작업실행_when_토큰일치() {
        controller.sync(VALID_TOKEN);
        controller.syncAcademicGuides(VALID_TOKEN);
        controller.rebuildVectors(VALID_TOKEN);

        verify(syncService).syncAll();
        verify(syncService).syncAcademicGuides();
        verify(syncService).rebuildVectorsOnly();
    }

    @Test
    @DisplayName("토큰이 없거나 틀리면 예외가 발생하고 색인을 건드리지 않는다")
    void should_예외및_미실행_when_토큰불일치() {
        assertThatThrownBy(() -> controller.sync(null)).isInstanceOf(SearchSyncException.class);
        assertThatThrownBy(() -> controller.syncAcademicGuides("wrong-token"))
                .isInstanceOf(SearchSyncException.class);
        assertThatThrownBy(() -> controller.rebuildVectors("")).isInstanceOf(SearchSyncException.class);

        verifyNoInteractions(syncService);
    }
}
