package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, Long> {

    /** 전체 카테고리 바텀시트용 - 그룹을 노출 순서대로 조회 */
    List<CategoryGroup> findAllByOrderByDisplayOrderAsc();

    /** 동기화 upsert용 - code로 단건 조회 */
    Optional<CategoryGroup> findByCode(String code);
}
