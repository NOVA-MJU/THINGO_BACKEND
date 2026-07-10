package nova.mjs.domain.thingo.block.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

/**
 * 사용자 차단 관계.
 *
 * - blocker 가 blocked 를 차단한 단방향 레코드 1건.
 * - "양방향 숨김"은 저장이 아니라 조회 시점에 (blocker=me OR blocked=me) 로 해석한다.
 *   (A가 B를 차단하면, A/B 서로의 글·댓글이 상대 화면에서 사라진다)
 * - (blocker, blocked) 조합은 유일하다.
 */
@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_block",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_block_pair",
                columnNames = {"blocker_id", "blocked_id"}
        )
)
public class Block extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_block_id")
    private Long id;

    /** 차단을 실행한 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private Member blocker;

    /** 차단당한 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private Member blocked;

    public static Block of(Member blocker, Member blocked) {
        return Block.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();
    }
}
