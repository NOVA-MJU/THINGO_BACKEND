package nova.mjs.domain.thingo.search.repository;

import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UnifiedSearchIndexRepository
        extends JpaRepository<UnifiedSearchIndex, String>, UnifiedSearchIndexQueryRepository {

    @Modifying
    @Query(value = "TRUNCATE TABLE unified_search_index", nativeQuery = true)
    void truncate();
}
