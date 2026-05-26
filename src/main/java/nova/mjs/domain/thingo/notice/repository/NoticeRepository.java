package nova.mjs.domain.thingo.notice.repository;

import nova.mjs.domain.thingo.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 카테고리 기준 조회
    Page<Notice> findByCategory(String category, Pageable pageable);

    // 카테고리 + 날짜 범위 조회
    Page<Notice> findByCategoryAndDateBetween(
            String category,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // 전체 조회 (Pageable 포함)
    Page<Notice> findAll(Pageable pageable);

    // 전체 + 날짜 범위 조회
    Page<Notice> findByDateBetween(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    // 조회수 기준 내림차순 조회 (한달 이내 공지사항)
    @Query("SELECT n FROM Notice n WHERE n.date >= :startDate ORDER BY n.viewCount DESC, n.date DESC")
    List<Notice> findHotNoticesWithinMonth(@Param("startDate") LocalDateTime startDate, Pageable pageable);

    // =========================
    // 크롤링 중 중복/교체 판단용
    // =========================

    /**
     * 동일 category + 동일 상세 link(enc) 존재 여부
     * - 완전 동일 게시글 판별에 사용한다.
     */
    boolean existsByCategoryAndLink(String category, String link);

    /**
     * 최근 특정 시점 이후(예: 1개월) 동일 title의 최신 공지 1건 조회
     * - 직원 재게시/수정 업로드 케이스에서 "교체 대상"을 찾는데 사용한다.
     */
    Optional<Notice> findTopByCategoryAndTitleAndDateAfterOrderByDateDesc(
            String category,
            String title,
            LocalDateTime dateAfter
    );

    /**
     * (선택) 최근 범위(예: 1개월) 내에서
     * 크롤링 목록에 존재하지 않는 DB row를 정리한다.
     *
     * 주의:
     * - 범위를 반드시 최근으로 제한해야 한다.
     * - linkNotIn 리스트가 너무 커지면 부담이 될 수 있으나,
     *   최근 1개월은 현실적으로 규모가 작아 안전하다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from Notice n
        where n.category = :category
          and n.date >= :threshold
          and n.link not in :links
    """)
    int deleteRecentNotInLinks(
            @Param("category") String category,
            @Param("threshold") LocalDateTime threshold,
            @Param("links") Collection<String> links
    );
}
