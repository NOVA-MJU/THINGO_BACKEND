package nova.mjs.domain.thingo.search.repository;

import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedSearchIndexRepository
        extends JpaRepository<UnifiedSearchIndex, String>, UnifiedSearchIndexQueryRepository {

    /**
     * 수동 알림 발송용: 키워드가 제목에 포함된 활성 콘텐츠 중 가장 최근 1건을 찾는다.
     *
     * - 알림 대상 카테고리 type(NOTICE/MJU_CALENDAR/COMMUNITY) 로 한정한다.
     * - 자동 키워드 매칭이 제목 토큰 기준인 것과 맞춰 제목(title) 부분일치로 후보를 고른다.
     * - keyword 의 LIKE 와일드카드(%, _)는 escape('\\') 로 리터럴 처리한다.
     */
    @Query(value = """
            SELECT * FROM unified_search_index
            WHERE active = true
              AND type IN (:types)
              AND title ILIKE ('%' || :keyword || '%') ESCAPE '\\'
            ORDER BY date DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UnifiedSearchIndex> findLatestActiveByTitleKeyword(@Param("keyword") String keyword,
                                                               @Param("types") List<String> types);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM unified_search_index WHERE type = ?1", nativeQuery = true)
    int deleteAllByType(String type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE unified_search_index SET
              search_vector = to_tsvector('simple', coalesce(search_tokens,''))
                           || to_tsvector('simple', coalesce(title,''))
                           || to_tsvector('simple', coalesce(content,'')),
              title_vector  = to_tsvector('simple', coalesce(title_tokens,''))
                           || to_tsvector('simple', coalesce(title,''))
            WHERE type = ?1
            """, nativeQuery = true)
    void rebuildVectorsByType(String type);

    /** 동일 link 중복 collapse 용. 같은 원문 링크를 가진 모든 행(활성/비활성 포함). */
    List<UnifiedSearchIndex> findByLink(String link);

    /**
     * 알림 backfill 후보: 최근 색인된 알림 대상 콘텐츠.
     *
     * reconcile(매일 04:00)은 인덱스에 직접 upsert 하고 이벤트를 발행하지 않아,
     * 실시간 경로를 놓친 콘텐츠는 검색에는 뜨지만 알림은 영영 나가지 않는다.
     * 그 누락분을 다시 매칭에 태우기 위한 조회다.
     *
     * @param since   색인 시각 하한 (이 시각 이후 색인된 행만)
     * @param minDate 콘텐츠 날짜 하한 (오래된 게시물 대량 소급 발송 방지)
     */
    @Query(value = """
            SELECT * FROM unified_search_index
            WHERE active = true
              AND type IN (:types)
              AND indexed_at >= :since
              AND date >= :minDate
            ORDER BY indexed_at
            LIMIT :maxRows
            """, nativeQuery = true)
    List<UnifiedSearchIndex> findAlarmBackfillCandidates(@Param("types") List<String> types,
                                                         @Param("since") Instant since,
                                                         @Param("minDate") Instant minDate,
                                                         @Param("maxRows") int maxRows);
}
