package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 지도 위의 한 점 = 건물 또는 장소(비건물). 명지도의 핵심 엔티티.
 *
 * 건물과 장소를 한 테이블로 합친 이유:
 * - 즐겨찾기 목록이 건물+장소를 섞어서 보여준다 (단일 FK로 단순화)
 * - 칩 클릭 목록·거리 정렬·핀 렌더링이 둘을 동일하게 다룬다
 * 종류별 전용 필드(건물=강의실코드, 장소=주소)는 nullable로 두고 type으로 구분한다.
 *
 * [code]
 * 구글 시트 동기화의 안정적 식별 키. 재동기화 시 code로 같은 핀을 찾아 갱신(upsert)하므로
 * 즐겨찾기가 끊기지 않는다. 모든 핀(건물/장소)은 시트에서 고유 code를 부여받는다.
 *
 * [좌표]
 * - 건물: 직접 입력
 * - 외부 장소: 등록 시 주소를 지오코딩해 저장(또는 시트에 직접 입력)
 * - 내부 장소(프린터/라운지): 자체 좌표 없이 소속 건물 좌표를 따른다 → latitude/longitude가 null일 수 있다
 *   (거리 계산 시 parentBuilding의 좌표로 대체한다)
 */
@Entity
@Table(
        name = "map_pin",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_pin_code",
                columnNames = "code"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_pin_id")
    private Long id;

    /** 동기화 식별 코드 (시트가 부여하는 고유 slug). upsert 키 */
    @Column(name = "code", nullable = false)
    private String code;

    /** 건물인지 장소인지 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PinType type;

    /** 소속 카테고리 (정확히 1개. 하위 탭이 있으면 leaf 카테고리를 가리킨다) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** 건물명/장소명 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 위도. 내부 장소는 null일 수 있음(소속 건물 좌표로 대체) */
    @Column(name = "latitude")
    private Double latitude;

    /** 경도. 내부 장소는 null일 수 있음(소속 건물 좌표로 대체) */
    @Column(name = "longitude")
    private Double longitude;

    /** Thingo 촬영 이미지 1장 URL. 없으면 null */
    @Column(name = "image_url")
    private String imageUrl;

    /** 정보(i) 아이콘에 표시할 기타 안내 텍스트. 없으면 null */
    @Column(name = "info_text", length = 1000)
    private String infoText;

    // ===== 건물(BUILDING) 전용 =====

    /** 건물 고유 번호 (1, 2, 3 ...). 강의실 코드의 근거 */
    @Column(name = "building_number")
    private Integer buildingNumber;

    /** 예시 강의실 코드 고정 텍스트 (예: "S1XXX"). 건물 상세에만 노출 */
    @Column(name = "classroom_code")
    private String classroomCode;

    // ===== 장소(PLACE) 전용 =====

    /** 도로명 주소 (외부 장소). 내부 장소는 null이고 위치는 건물명+층수로 표시 */
    @Column(name = "address")
    private String address;

    /** 내부 장소가 속한 건물 (Pin, type=BUILDING). 외부 장소는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_building_id")
    private Pin parentBuilding;

    /** 내부 장소가 위치한 층. 외부 장소는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    // ===== 연관 (건물 기준) =====

    /** 건물에 속한 층 목록 (건물이 아닐 경우 빈 리스트) */
    @OneToMany(mappedBy = "building", fetch = FetchType.LAZY)
    private List<Floor> floors = new ArrayList<>();

    /** 요일별 운영시간 목록 (없으면 운영 상태 미표시) */
    @OneToMany(mappedBy = "pin", fetch = FetchType.LAZY)
    private List<OperatingHour> operatingHours = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Pin(String code, PinType type, Category category, String name, Double latitude, Double longitude,
               String imageUrl, String infoText, Integer buildingNumber, String classroomCode,
               String address, Pin parentBuilding, Floor floor) {
        this.code = code;
        this.type = type;
        this.category = category;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.infoText = infoText;
        this.buildingNumber = buildingNumber;
        this.classroomCode = classroomCode;
        this.address = address;
        this.parentBuilding = parentBuilding;
        this.floor = floor;
    }

    /**
     * 건물 핀 생성. 강의실 코드·건물 번호 등 건물 전용 정보를 받는다.
     */
    public static Pin ofBuilding(String code, Category category, String name, Double latitude, Double longitude,
                                 String imageUrl, String infoText, Integer buildingNumber, String classroomCode) {
        return Pin.builder()
                .code(code)
                .type(PinType.BUILDING)
                .category(category)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .imageUrl(imageUrl)
                .infoText(infoText)
                .buildingNumber(buildingNumber)
                .classroomCode(classroomCode)
                .build();
    }

    /**
     * 외부 장소 핀 생성. 도로명 주소와 (지오코딩으로 얻은) 좌표를 받는다.
     */
    public static Pin ofExternalPlace(String code, Category category, String name, Double latitude, Double longitude,
                                      String imageUrl, String infoText, String address) {
        return Pin.builder()
                .code(code)
                .type(PinType.PLACE)
                .category(category)
                .name(name)
                .latitude(latitude)
                .longitude(longitude)
                .imageUrl(imageUrl)
                .infoText(infoText)
                .address(address)
                .build();
    }

    /**
     * 건물 내부 장소 핀 생성 (프린터/라운지 등). 좌표는 소속 건물을 따르므로 받지 않는다.
     */
    public static Pin ofInternalPlace(String code, Category category, String name, String imageUrl, String infoText,
                                      Pin parentBuilding, Floor floor) {
        return Pin.builder()
                .code(code)
                .type(PinType.PLACE)
                .category(category)
                .name(name)
                .imageUrl(imageUrl)
                .infoText(infoText)
                .parentBuilding(parentBuilding)
                .floor(floor)
                .build();
    }

    /**
     * 동기화 갱신. code/type은 유지하고 나머지 속성만 시트 값으로 덮어쓴다.
     * 종류와 무관한 필드(건물 전용/장소 전용)는 해당 종류가 아니면 null로 전달된다.
     */
    public void update(Category category, String name, Double latitude, Double longitude,
                       String imageUrl, String infoText, Integer buildingNumber, String classroomCode,
                       String address, Pin parentBuilding, Floor floor) {
        this.category = category;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.imageUrl = imageUrl;
        this.infoText = infoText;
        this.buildingNumber = buildingNumber;
        this.classroomCode = classroomCode;
        this.address = address;
        this.parentBuilding = parentBuilding;
        this.floor = floor;
    }

    /** 거리 계산에 쓸 위도. 내부 장소면 소속 건물 위도로 대체. 둘 다 없으면 null */
    public Double resolveLatitude() {
        if (latitude != null) {
            return latitude;
        }
        return parentBuilding != null ? parentBuilding.getLatitude() : null;
    }

    /** 거리 계산에 쓸 경도. 내부 장소면 소속 건물 경도로 대체. 둘 다 없으면 null */
    public Double resolveLongitude() {
        if (longitude != null) {
            return longitude;
        }
        return parentBuilding != null ? parentBuilding.getLongitude() : null;
    }

    /** 건물 내부 장소인지 여부 (소속 건물이 있으면 내부) */
    public boolean isInsideBuilding() {
        return parentBuilding != null;
    }
}
