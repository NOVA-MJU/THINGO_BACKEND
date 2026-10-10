package nova.mjs.domain.thingo.search.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnifiedSearchIndexQueryRepositoryImplTest {

    @Test
    @DisplayName("유형별 상위 검색은 쿼리 한 번으로 유형마다 순위를 매기고 요청한 유형만 조회한다")
    void searchTopPerType_ranksWithinEachTypeInSingleQuery() {
        EntityManager em = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        UnifiedSearchIndexQueryRepositoryImpl repository = new UnifiedSearchIndexQueryRepositoryImpl();
        ReflectionTestUtils.setField(repository, "em", em);

        repository.searchTopPerType("장학금", List.of("NOTICE", "NEWS"), 3, null, 0d);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .contains("ROW_NUMBER() OVER (PARTITION BY type ORDER BY score DESC, date DESC NULLS LAST)")
                .contains("type IN (:types)")
                .contains("r.rn <= :perType");
        verify(query).setParameter("types", List.of("NOTICE", "NEWS"));
        verify(query).setParameter("perType", 3);
    }

    @Test
    @DisplayName("검색어가 없거나 유형이 비면 DB를 조회하지 않는다")
    void searchTopPerType_skipsQueryWithoutKeywordOrTypes() {
        EntityManager em = mock(EntityManager.class);
        UnifiedSearchIndexQueryRepositoryImpl repository = new UnifiedSearchIndexQueryRepositoryImpl();
        ReflectionTestUtils.setField(repository, "em", em);

        assertThat(repository.searchTopPerType(" ", List.of("NOTICE"), 3, null, 0d)).isEmpty();
        assertThat(repository.searchTopPerType("장학금", List.of(), 3, null, 0d)).isEmpty();
        verifyNoInteractions(em);
    }
}
