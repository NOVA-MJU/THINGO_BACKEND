package nova.mjs.domain.thingo.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;

/**
 * 리뷰 좋아요. 게시판 CommunityLike 미러.
 * (member, review) 유니크 제약으로 중복 좋아요를 DB 수준에서 차단한다.
 */
@Entity
@Table(
        name = "like_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_like_review_member_review",
                columnNames = {"member_id", "map_review_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_review_id", nullable = false)
    private Review review;

    public ReviewLike(Member member, Review review) {
        this.member = member;
        this.review = review;
    }
}
