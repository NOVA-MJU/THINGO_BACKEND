package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.entity.BaseEntity;

/**
 * 건물의 한 개 층.
 *
 * 건물(Pin, type=BUILDING)에 1:N으로 딸린다.
 * 층별 안내도 이미지(mapImageUrl)와 정렬 순서(floorOrder)를 가진다.
 * 건물 안 장소(Pin, type=PLACE)는 자신이 위치한 Floor를 참조한다.
 */
@Entity
@Table(name = "map_floor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_floor_id")
    private Long id;

    /** 이 층이 속한 건물 (Pin, type=BUILDING) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Pin building;

    /** 층 표시 라벨 (예: "B1", "F1") */
    @Column(name = "label", nullable = false)
    private String label;

    /** 층 정렬 순서 (지하는 음수, 지상은 양수. 예: B1=-1, 1F=1). 낮은 층이 아래 */
    @Column(name = "floor_order", nullable = false)
    private int floorOrder;

    /** Thingo 자체 제작 층별 안내도 이미지 URL. 없으면 null */
    @Column(name = "map_image_url")
    private String mapImageUrl;

    @Builder(access = AccessLevel.PRIVATE)
    private Floor(Pin building, String label, int floorOrder, String mapImageUrl) {
        this.building = building;
        this.label = label;
        this.floorOrder = floorOrder;
        this.mapImageUrl = mapImageUrl;
    }

    /**
     * 층 생성. 어떤 건물의 몇 층인지와 안내도 이미지를 받는다.
     */
    public static Floor of(Pin building, String label, int floorOrder, String mapImageUrl) {
        return Floor.builder()
                .building(building)
                .label(label)
                .floorOrder(floorOrder)
                .mapImageUrl(mapImageUrl)
                .build();
    }
}
