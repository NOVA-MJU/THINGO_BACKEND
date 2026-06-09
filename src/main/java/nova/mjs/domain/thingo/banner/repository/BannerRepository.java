package nova.mjs.domain.thingo.banner.repository;

import nova.mjs.domain.thingo.banner.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /* 활성 배너 전체 (노출 순서 오름차순) */
    List<Banner> findByActiveTrueOrderByDisplayOrderAsc();

    /* 카테고리별 활성 배너 (노출 순서 오름차순) */
    List<Banner> findByActiveTrueAndCategoryOrderByDisplayOrderAsc(String category);
}
