package nova.mjs.domain.thingo.search.repository;

import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnifiedSearchIndexRepository
        extends JpaRepository<UnifiedSearchIndex, String>, UnifiedSearchIndexQueryRepository {

    @Modifying
    @Query(value = "TRUNCATE TABLE unified_search_index", nativeQuery = true)
    void truncate();

    /** 동일 link 중복 collapse 용. 같은 원문 링크를 가진 모든 행(활성/비활성 포함). */
    List<UnifiedSearchIndex> findByLink(String link);
}
