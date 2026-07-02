package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PinRepository extends JpaRepository<Pin, Long> {

    /** 종류별 핀 조회 (예: 건물 목록). 건물 번호 순 정렬 */
    List<Pin> findByTypeOrderByBuildingNumberAsc(PinType type);

    /**
     * 주어진 카테고리 코드들에 속한 핀 조회.
     * 칩 클릭 시 사용한다. 하위 탭이 있는 칩(대동명지도)은 자식 코드들까지 함께 넘긴다.
     */
    @Query("select p from Pin p where p.category.code in :codes")
    List<Pin> findByCategoryCodeIn(@Param("codes") Collection<String> codes);

    /** 종류를 확정한 단건 조회 (건물 상세는 BUILDING, 장소 상세는 PLACE) */
    Optional<Pin> findByIdAndType(Long id, PinType type);

    /** 특정 건물에 속한 내부 장소들 (층별 시설 목록 구성용) */
    List<Pin> findByParentBuildingId(Long buildingId);

    /** 동기화 upsert용 - code로 단건 조회 */
    Optional<Pin> findByCode(String code);

    /**
     * 검색용 전체 핀 조회. 관련도 스코어링/카드 구성에 필요한 연관을 fetch join으로 함께 로딩한다.
     * (카테고리는 이름·라벨·아이콘, 소속 건물/층은 내부 장소의 위치·운영시간 상속에 쓰인다)
     * 캠퍼스 규모(핀 수백 개)라 전체를 메모리에 올려 스코어링해도 충분하다.
     */
    @Query("select distinct p from Pin p "
            + "join fetch p.category "
            + "left join fetch p.parentBuilding "
            + "left join fetch p.floor")
    List<Pin> findAllForSearch();
}
