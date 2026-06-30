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

    /**
     * search_vector / title_vector 를 모든 행에 대해 직접 재생성한다.
     *
     * DB 트리거(usi_update_search_vector)에 의존하지 않는 안전망. 운영 RDS 에서 schema-init 의
     * 트리거 재생성이 실패하면(권한/타이밍) 재색인된 행의 search_vector 가 NULL 로 남아 키워드 검색이
     * 전부 0 이 되는 사고가 난다. 재색인 말미에 이 쿼리를 호출해 트리거 유무와 무관하게 벡터를 보장한다.
     *
     * flushAutomatically: 직전 saveAll/변경분이 DB 에 반영된 뒤 UPDATE 가 실행되도록 강제(영속성
     * 컨텍스트 flush). clearAutomatically: 갱신 후 1차 캐시의 낡은 엔티티를 비운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE unified_search_index SET
              search_vector = to_tsvector('simple', coalesce(search_tokens,''))
                           || to_tsvector('simple', coalesce(title,''))
                           || to_tsvector('simple', coalesce(content,'')),
              title_vector  = to_tsvector('simple', coalesce(title_tokens,''))
                           || to_tsvector('simple', coalesce(title,''))
            """, nativeQuery = true)
    void rebuildVectors();

    /** 동일 link 중복 collapse 용. 같은 원문 링크를 가진 모든 행(활성/비활성 포함). */
    List<UnifiedSearchIndex> findByLink(String link);
}
