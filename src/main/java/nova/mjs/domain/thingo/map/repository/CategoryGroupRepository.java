package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, Long> {

    /** 동기화 upsert용 - code로 단건 조회 */
    Optional<CategoryGroup> findByCode(String code);
}
