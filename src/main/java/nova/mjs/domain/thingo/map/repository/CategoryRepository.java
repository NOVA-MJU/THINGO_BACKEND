package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 칩 코드로 단건 조회 (예: "daedong") */
    Optional<Category> findByCode(String code);

    /** 특정 칩의 하위 탭들 (예: 대동명지도 아래 한식/중식), 노출 순서대로 */
    List<Category> findByParentOrderByDisplayOrderAsc(Category parent);
}
