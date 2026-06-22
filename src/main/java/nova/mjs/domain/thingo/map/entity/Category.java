package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.entity.BaseEntity;

/**
 * 칩(카테고리). 명지도에서 장소/건물을 묶고 필터링하는 단위.
 *
 * 하나의 Category가 두 역할을 겸한다.
 * - 상단 퀵메뉴 칩 (quickMenu=true)
 * - 건물 상세의 카테고리 필터 탭
 *
 * [자기참조 트리]
 * parent가 null이면 최상위 칩(예: 대동명지도), parent가 있으면 그 칩의 하위 탭이다.
 * 예) 대동명지도(parent=null) 아래 한식/중식/일식(parent=대동명지도).
 * 하위 탭이 필요한 다른 칩도 행만 추가하면 되므로 스키마 변경 없이 범용 확장된다.
 *
 * 출처/제휴 표기(예: "by. 명월")는 별도 필드를 두지 않고 subtitle로 범용 처리한다.
 */
@Entity
@Table(
        name = "map_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_category_code",
                columnNames = "code"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_category_id")
    private Long id;

    /** 칩 식별 코드 (예: daedong, korean, printer). 프론트가 참조하는 안정적 키 */
    @Column(name = "code", nullable = false)
    private String code;

    /** 소속 대분류 그룹 (식사/학습·휴식/편의 등) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private CategoryGroup group;

    /** 상위 칩. null이면 최상위 칩, 값이 있으면 하위 탭(예: 대동명지도 아래 한식) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    /** 칩 표시 이름 (예: "대동명지도", "한식") */
    @Column(name = "label", nullable = false)
    private String label;

    /** 부제/출처 텍스트 (예: "by. 명월"). 없으면 null */
    @Column(name = "subtitle")
    private String subtitle;

    /** 메인 홈 진입 시 노출되는 툴팁 문구. 없으면 null */
    @Column(name = "tooltip_text")
    private String tooltipText;

    /** 아이콘 식별 키 (프론트 아이콘 레지스트리가 컴포넌트로 매핑, 예: "MyeongwolIcon") */
    @Column(name = "icon_key")
    private String iconKey;

    /** 칩 클릭 시 결과 종류 (장소 목록 / 건물 목록 / 버스 화면 이동) */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false)
    private CategoryResultType resultType;

    /** 상단 퀵메뉴 칩으로 기본 노출할지 여부 */
    @Column(name = "quick_menu", nullable = false)
    private boolean quickMenu;

    /** 노출 순서 (그룹 내/퀵메뉴 내 정렬, 작을수록 앞). 운영자가 조정 */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder(access = AccessLevel.PRIVATE)
    private Category(String code, CategoryGroup group, Category parent, String label,
                     String subtitle, String tooltipText, String iconKey,
                     CategoryResultType resultType, boolean quickMenu, int displayOrder) {
        this.code = code;
        this.group = group;
        this.parent = parent;
        this.label = label;
        this.subtitle = subtitle;
        this.tooltipText = tooltipText;
        this.iconKey = iconKey;
        this.resultType = resultType;
        this.quickMenu = quickMenu;
        this.displayOrder = displayOrder;
    }

    /**
     * 최상위 칩 생성 (parent 없음).
     */
    public static Category ofChip(String code, CategoryGroup group, String label, String subtitle,
                                  String tooltipText, String iconKey, CategoryResultType resultType,
                                  boolean quickMenu, int displayOrder) {
        return Category.builder()
                .code(code)
                .group(group)
                .parent(null)
                .label(label)
                .subtitle(subtitle)
                .tooltipText(tooltipText)
                .iconKey(iconKey)
                .resultType(resultType)
                .quickMenu(quickMenu)
                .displayOrder(displayOrder)
                .build();
    }

    /**
     * 하위 탭 생성 (예: 대동명지도 아래 한식). 그룹/결과종류는 부모를 따른다.
     */
    public static Category ofSubTab(String code, Category parent, String label,
                                    String iconKey, int displayOrder) {
        return Category.builder()
                .code(code)
                .group(parent.getGroup())
                .parent(parent)
                .label(label)
                .iconKey(iconKey)
                .resultType(parent.getResultType())
                .quickMenu(false)
                .displayOrder(displayOrder)
                .build();
    }

    /** 최상위 칩(하위 탭이 아님)인지 여부 */
    public boolean isTopLevel() {
        return parent == null;
    }

    /** 동기화 갱신 (code는 유지) */
    public void update(CategoryGroup group, Category parent, String label, String subtitle,
                       String tooltipText, String iconKey, CategoryResultType resultType,
                       boolean quickMenu, int displayOrder) {
        this.group = group;
        this.parent = parent;
        this.label = label;
        this.subtitle = subtitle;
        this.tooltipText = tooltipText;
        this.iconKey = iconKey;
        this.resultType = resultType;
        this.quickMenu = quickMenu;
        this.displayOrder = displayOrder;
    }
}
