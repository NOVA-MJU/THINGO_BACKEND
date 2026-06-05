package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

/**
 * 버스 노선 즐겨찾기
 *
 * - 한 회원이 특정 정류장(arsId)의 특정 노선(routeName)을 즐겨찾기한 기록
 * - (member, arsId, routeName) 조합은 유일 (중복 즐겨찾기 방지)
 * - 즐겨찾기한 노선은 도착 정보 목록에서 상단으로 정렬된다
 */
@Entity
@Table(
        name = "bus_favorite",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bus_favorite_member_station_route",
                columnNames = {"member_id", "ars_id", "route_name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusFavorite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bus_favorite_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 정류장 고유 ID (ARS 번호) */
    @Column(name = "ars_id", nullable = false)
    private String arsId;

    /** 버스 노선 번호 (예: 7611) */
    @Column(name = "route_name", nullable = false)
    private String routeName;

    @Builder(access = AccessLevel.PRIVATE)
    private BusFavorite(Member member, String arsId, String routeName) {
        this.member = member;
        this.arsId = arsId;
        this.routeName = routeName;
    }

    public static BusFavorite of(Member member, String arsId, String routeName) {
        return BusFavorite.builder()
                .member(member)
                .arsId(arsId)
                .routeName(routeName)
                .build();
    }
}
