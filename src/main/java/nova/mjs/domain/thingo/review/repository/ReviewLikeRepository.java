package nova.mjs.domain.thingo.review.repository;

import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ReviewLikeRepository. 좋아요 토글/중복 판정 + 목록 isLiked 일괄 조회.
 */
@Repository
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByMemberAndReview(Member member, Review review);

    boolean existsByMemberAndReview(Member member, Review review);

    /** 주어진 리뷰 uuid 집합 중 해당 회원이 좋아요한 uuid만 반환(목록 isLiked 계산용) */
    @Query("""
        select rl.review.uuid
        from ReviewLike rl
        where rl.member.id = :memberId
          and rl.review.uuid in :reviewUuids
    """)
    List<UUID> findLikedReviewUuids(@Param("memberId") Long memberId,
                                    @Param("reviewUuids") Collection<UUID> reviewUuids);
}
