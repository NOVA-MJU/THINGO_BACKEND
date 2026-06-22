package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 칩 코드로 단건 조회 (예: "daedong") */
    Optional<Category> findByCode(String code);

    /** 상단 퀵메뉴 칩 - 최상위(부모 없음) + quickMenu=true, 노출 순서대로 */
    List<Category> findByQuickMenuTrueAndParentIsNullOrderByDisplayOrderAsc();

    /** 전체 카테고리 바텀시트용 - 최상위 칩 전체를 노출 순서대로 */
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();

    /** 특정 칩의 하위 탭들 (예: 대동명지도 아래 한식/중식), 노출 순서대로 */
    List<Category> findByParentOrderByDisplayOrderAsc(Category parent);
}
