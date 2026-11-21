package nova.mjs.domain.thingo.review.repository;

import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewMedia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * ReviewRepository
 *
 * 역할
 * - 리뷰 단건 조회(uuid), 장소별 페이지 조회(최신순)
 * - 차단 사용자 제외 조회(author id NOT IN)
 * - 좋아요 집계 컬럼(likeCount) 원자적 증감
 *
 * 주의
 * - 목록 쿼리는 author만 EntityGraph로 즉시 로딩한다(닉네임·차단 판정용).
 *   keywords/media 컬렉션은 페이지네이션과 fetch join이 충돌하므로 서비스에서 처리한다.
 * - NOT IN (:hidden) 은 hidden이 비어있으면 오류가 날 수 있어 서비스에서 분기한다
 *   (커뮤니티와 동일 정책).
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 최신순 커서 조회. 제외 집합은 서비스가 빈 값 대신 절대 매칭되지 않는 sentinel을 전달한다.
     * size+1개를 요청해 다음 페이지 존재 여부를 count 쿼리 없이 판정한다.
     */
    @EntityGraph(attributePaths = "author")
    @Query("""
        select r
        from Review r
        where r.pin.id = :pinId
          and r.hidden = false
          and r.author.id not in :excludedAuthorIds
          and r.uuid not in :excludedReviewUuids
          and (
              :cursorId is null
              or r.createdAt < :cursorCreatedAt
              or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
          )
        order by r.createdAt desc, r.id desc
    """)
    List<Review> findLatestCursor(
            @Param("pinId") Long pinId,
            @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
            @Param("excludedReviewUuids") Collection<UUID> excludedReviewUuids,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 좋아요순: 좋아요 수 → 작성일 → 내부 id 순으로 안정적인 동률 순서를 만든다. */
    @EntityGraph(attributePaths = "author")
    @Query("""
        select r
        from Review r
        where r.pin.id = :pinId
          and r.hidden = false
          and r.author.id not in :excludedAuthorIds
          and r.uuid not in :excludedReviewUuids
          and (
              :cursorId is null
              or r.likeCount < :cursorLikeCount
              or (r.likeCount = :cursorLikeCount and r.createdAt < :cursorCreatedAt)
              or (r.likeCount = :cursorLikeCount and r.createdAt = :cursorCreatedAt and r.id < :cursorId)
          )
        order by r.likeCount desc, r.createdAt desc, r.id desc
    """)
    List<Review> findLikesCursor(
            @Param("pinId") Long pinId,
            @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
            @Param("excludedReviewUuids") Collection<UUID> excludedReviewUuids,
            @Param("cursorLikeCount") Integer cursorLikeCount,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 현재 뷰어에게 노출되는 총 리뷰 수(상단 '리뷰 N' 표기용). */
    @Query("""
        select count(r)
        from Review r
        where r.pin.id = :pinId
          and r.hidden = false
          and r.author.id not in :excludedAuthorIds
          and r.uuid not in :excludedReviewUuids
    """)
    long countVisible(
            @Param("pinId") Long pinId,
            @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
            @Param("excludedReviewUuids") Collection<UUID> excludedReviewUuids);

    /**
     * 장소 상단 사진·영상 스트립. 미디어가 없는 리뷰 때문에 결과가 덜 채워지지 않도록
     * 리뷰가 아닌 미디어를 직접 최신 리뷰 순서로 조회한다.
     */
    @Query("""
        select m
        from ReviewMedia m
        join fetch m.review r
        where r.pin.id = :pinId
          and r.hidden = false
          and r.author.id not in :excludedAuthorIds
          and r.uuid not in :excludedReviewUuids
        order by r.createdAt desc, r.id desc, m.sortOrder asc
    """)
    List<ReviewMedia> findVisibleMedia(
            @Param("pinId") Long pinId,
            @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
            @Param("excludedReviewUuids") Collection<UUID> excludedReviewUuids,
            Pageable pageable);

    /**
     * 상세 조회: author + media 즉시 로딩.
     * keywords(Set)까지 함께 fetch하면 media(List bag)와 카테시안 곱으로 중복되므로,
     * keywords는 lazy로 두고 서비스 트랜잭션 안에서 별도 로딩한다.
     */
    @EntityGraph(attributePaths = {"author", "media"})
    Optional<Review> findByUuid(UUID uuid);

    /** 장소별 최신순 페이지 (자동숨김 제외, 차단 필터 미적용) */
    @EntityGraph(attributePaths = "author")
    Page<Review> findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(Long pinId, Pageable pageable);

    /** 장소별 최신순 페이지 (자동숨김 + 차단 사용자 author id 제외) */
    @EntityGraph(attributePaths = "author")
    Page<Review> findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(
            Long pinId, Collection<Long> blockedAuthorIds, Pageable pageable);

    /** 운영자 검토 큐: 자동 숨김된 리뷰 목록(최신순) */
    @EntityGraph(attributePaths = "author")
    List<Review> findByHiddenTrueOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Review r
        set r.likeCount = r.likeCount + 1
        where r.uuid = :uuid
    """)
    int increaseLikeCount(@Param("uuid") UUID uuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Review r
        set r.likeCount =
            case when r.likeCount > 0 then r.likeCount - 1 else 0 end
        where r.uuid = :uuid
    """)
    int decreaseLikeCount(@Param("uuid") UUID uuid);

    @Query("""
        select r.likeCount
        from Review r
        where r.uuid = :uuid
    """)
    Integer findLikeCount(@Param("uuid") UUID uuid);

    /** 리뷰가 화면에 보인 횟수(조회수)를 한 번에 1씩 올린다. 없는 uuid는 무시된다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Review r
        set r.viewCount = r.viewCount + 1
        where r.uuid in :uuids
    """)
    int increaseViewCount(@Param("uuids") Collection<UUID> uuids);

    /** 내가 쓴 리뷰(숨김 제외, 최신순). 장소 이름·좌표를 같이 보여주려고 pin을 함께 읽는다 */
    @EntityGraph(attributePaths = {"pin", "author"})
    List<Review> findByAuthor_IdAndHiddenFalseOrderByCreatedAtDesc(Long authorId);
}
