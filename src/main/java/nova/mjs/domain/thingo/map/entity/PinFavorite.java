package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

/**
 * 핀(건물/장소) 즐겨찾기.
 *
 * 한 회원이 특정 핀을 '마이 즐겨찾기 리스트'에 담은 기록.
 * Pin이 건물·장소를 통합했기 때문에 즐겨찾기도 단일 FK(pin)로 둘 다 다룬다.
 * (member, pin) 조합은 유일 (중복 즐겨찾기 방지).
 * 즐겨찾기된 핀은 칩 목록에서 상단으로 정렬된다.
 */
@Entity
@Table(
        name = "map_pin_favorite",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_pin_favorite_member_pin",
                columnNames = {"member_id", "pin_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinFavorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_pin_favorite_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pin_id", nullable = false)
    private Pin pin;

    @Builder(access = AccessLevel.PRIVATE)
    private PinFavorite(Member member, Pin pin) {
        this.member = member;
        this.pin = pin;
    }

    public static PinFavorite of(Member member, Pin pin) {
        return PinFavorite.builder()
                .member(member)
                .pin(pin)
                .build();
    }
}
