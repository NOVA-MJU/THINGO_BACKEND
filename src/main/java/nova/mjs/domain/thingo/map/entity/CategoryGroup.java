package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.entity.BaseEntity;

/**
 * 카테고리 대분류 (전체 카테고리 바텀시트의 큰 섹션).
 *
 * '더보기(···)'를 눌렀을 때 뜨는 전체 카테고리 화면에서 칩들을 묶는 헤더다.
 * 예) 식사(F&B), 학습·휴식(Study/Rest), 편의(Convenience)
 *
 * 칩(Category)들이 이 그룹에 소속되며, 그룹은 화면 노출 순서(displayOrder)와
 * 그룹 아이콘 색상(colorHex)을 갖는다.
 */
@Entity
@Table(
        name = "map_category_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_category_group_code",
                columnNames = "code"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_category_group_id")
    private Long id;

    /** 그룹 식별 코드 (예: food, study, convenience). 프론트가 참조하는 안정적 키 */
    @Column(name = "code", nullable = false)
    private String code;

    /** 그룹 표시 이름 (예: "식사 (F&B)") */
    @Column(name = "name", nullable = false)
    private String name;

    /** 그룹 아이콘 강조 색상 (HEX, 예: #F57F36). 프론트가 Tailwind 등으로 조립 */
    @Column(name = "color_hex")
    private String colorHex;

    /** 바텀시트에서 그룹이 노출되는 순서 (작을수록 위) */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private CategoryGroup(String code, String name, String colorHex, int displayOrder) {
        this.code = code;
        this.name = name;
        this.colorHex = colorHex;
        this.displayOrder = displayOrder;
    }

    /**
     * 카테고리 그룹 생성.
     * 운영 데이터는 추후 구글 시트 동기화로 채워지며, 시드/테스트에서 이 팩토리를 사용한다.
     */
    public static CategoryGroup of(String code, String name, String colorHex, int displayOrder) {
        return CategoryGroup.builder()
                .code(code)
                .name(name)
                .colorHex(colorHex)
                .displayOrder(displayOrder)
                .build();
    }
}
