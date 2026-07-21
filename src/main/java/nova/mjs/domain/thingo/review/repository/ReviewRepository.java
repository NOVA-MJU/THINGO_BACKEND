package nova.mjs.domain.thingo.review.repository;

import nova.mjs.domain.thingo.review.entity.Review;
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
}
