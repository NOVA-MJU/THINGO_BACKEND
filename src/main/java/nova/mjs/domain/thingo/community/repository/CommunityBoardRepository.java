package nova.mjs.domain.thingo.community.repository;

import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.community.entity.enumList.CommunityCategory;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CommunityBoardRepository
 *
 * 역할
 * - 게시글 단건 조회, 페이징 조회
 * - 인기글 조회 (likeCount DESC 상위 N개)
 * - 카테고리별 조회/제외 조회
 * - 좋아요/댓글 집계 컬럼(likeCount/commentCount) 원자적 증감
 *
 * 주의
 * - @EntityGraph(attributePaths = "author") 로 author를 항상 함께 로딩해서
 *   Service 단에서 N+1 문제 없이 작성자 닉네임 등을 바로 DTO로 변환할 수 있도록 한다.
 * - NOT IN (:excluded) 는 excluded가 비어있으면 오류가 날 수 있으니 Service에서 분기 처리한다.
 * - 컬렉션 fetch join + 페이징은 위험하므로 댓글 fetch join 메서드는 상세 조회 전용으로만 사용한다.
 */
@Repository
public interface CommunityBoardRepository extends JpaRepository<CommunityBoard, Long> {

    Optional<CommunityBoard> findByUuid(UUID uuid);

    /**
     * 신고 누적으로 자동 숨김된 게시글 목록 (운영자 검토 큐, L2).
     * author 를 함께 로딩해 목록 변환 시 N+1 을 막는다.
     */
    @EntityGraph(attributePaths = "author")
    List<CommunityBoard> findByHiddenTrueOrderByCreatedAtDesc();

    @Query("""
        SELECT cb
        FROM CommunityBoard cb
        JOIN FETCH cb.comment
        WHERE cb.uuid = :uuid
    """)
    Optional<CommunityBoard> findByUuidWithComment(@Param("uuid") UUID uuid);

    Page<CommunityBoard> findByAuthor(Member author, Pageable pageable);

    int countByAuthor(Member author);

    /**
     * ===== 인기글 조회 =====
     *
     * - 최근 after 이후 작성(publishedAt >= after)
     * - 공개글(published=true)
     * - likeCount DESC, createdAt DESC
     * - Pageable로 상위 N개 제한
     *
     * 주의
     * - 메서드명이 top3로 고정돼 보이지만, 실제로는 Pageable로 N을 제어한다.
     *   (서비스에서 PageRequest.of(0,3)을 넘기므로 top3가 된다)
     */
    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.publishedAt >= :after
          AND b.published = true
          AND b.hidden = false
        ORDER BY b.likeCount DESC, b.createdAt DESC
    """)
    List<CommunityBoard> findTop3PopularBoards(
            @Param("after") LocalDateTime after,
            Pageable pageable
    );

    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.publishedAt >= :after
          AND b.published = true
          AND b.hidden = false
          AND b.category = :category
        ORDER BY b.likeCount DESC, b.createdAt DESC
    """)
    List<CommunityBoard> findTop3PopularBoardsByCategory(
            @Param("after") LocalDateTime after,
            @Param("category") CommunityCategory category,
            Pageable pageable
    );

    /**
     * ===== HOT 게시글 조회 =====
     *
     * 정책
     * - 전체 기간 공개글(published=true) 대상
     * - 점수 = viewCount + 2 * likeCount, DESC
     * - 동률은 createdAt DESC
     * - Pageable로 N(프론트에서 size 지정, 기본 7) 제어
     *
     * 주의
     * - author EntityGraph로 N+1 방지 (DTO 변환 시 nickname 접근)
     * - 추후 슬라이딩 윈도우 도입 시 일별 집계 테이블 + 별도 쿼리로 분리 예정
     */
    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT c
        FROM CommunityBoard c
        WHERE c.published = true
          AND c.hidden = false
        ORDER BY (c.viewCount + 2 * c.likeCount) DESC, c.createdAt DESC
    """)
    List<CommunityBoard> findHotBoards(Pageable pageable);

    /**
     * ===== 일반글 / 전체글 조회 =====
     *
     * author 즉시 로딩으로 N+1 방지
     */
    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.hidden = false
    """)
    Page<CommunityBoard> findAllWithAuthor(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.hidden = false
          AND b.uuid NOT IN :excluded
    """)
    Page<CommunityBoard> findAllWithAuthorExcluding(
            @Param("excluded") List<UUID> excluded,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.hidden = false
          AND b.category = :category
    """)
    Page<CommunityBoard> findAllWithAuthorByCategory(
            @Param("category") CommunityCategory category,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT b
        FROM CommunityBoard b
        WHERE b.hidden = false
          AND b.category = :category
          AND b.uuid NOT IN :excluded
    """)
    Page<CommunityBoard> findAllWithAuthorByCategoryExcluding(
            @Param("category") CommunityCategory category,
            @Param("excluded") List<UUID> excluded,
            Pageable pageable
    );

    /**
     * ===== 집계 컬럼 원자적 증감 =====
     *
     * - likeCount/commentCount는 이벤트 시점에만 업데이트한다.
     * - 벌크 업데이트이므로 영속성 컨텍스트와 불일치가 발생할 수 있어
     *   clearAutomatically/flushAutomatically로 안전성을 높인다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CommunityBoard cb
        set cb.likeCount = cb.likeCount + 1
        where cb.uuid = :uuid
    """)
    int increaseLikeCount(@Param("uuid") UUID uuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CommunityBoard cb
        set cb.likeCount =
            case when cb.likeCount > 0 then cb.likeCount - 1 else 0 end
        where cb.uuid = :uuid
    """)
    int decreaseLikeCount(@Param("uuid") UUID uuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CommunityBoard cb
        set cb.commentCount = cb.commentCount + 1
        where cb.uuid = :uuid
    """)
    int increaseCommentCount(@Param("uuid") UUID uuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update CommunityBoard cb
        set cb.commentCount =
            case when cb.commentCount > 0 then cb.commentCount - 1 else 0 end
        where cb.uuid = :uuid
    """)
    int decreaseCommentCount(@Param("uuid") UUID uuid);

    @Query("""
        select cb.likeCount
        from CommunityBoard cb
        where cb.uuid = :uuid
    """)
    int findLikeCount(@Param("uuid") UUID uuid);

    @Query("""
        select cb.commentCount
        from CommunityBoard cb
        where cb.uuid = :uuid
    """)
    int findCommentCount(@Param("uuid") UUID uuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update CommunityBoard cb
    set cb.commentCount = cb.commentCount + :delta
    where cb.uuid = :uuid
""")
    int increaseCommentCountBy(@Param("uuid") UUID uuid, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update CommunityBoard cb
    set cb.commentCount =
        case when cb.commentCount >= :delta then cb.commentCount - :delta else 0 end
    where cb.uuid = :uuid
""")
    int decreaseCommentCountBy(@Param("uuid") UUID uuid, @Param("delta") int delta);

}
