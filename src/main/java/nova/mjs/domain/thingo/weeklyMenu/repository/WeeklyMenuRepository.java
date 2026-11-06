package nova.mjs.domain.thingo.weeklyMenu.repository;


import nova.mjs.domain.thingo.weeklyMenu.entity.WeeklyMenu;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeeklyMenuRepository extends JpaRepository<WeeklyMenu, Long> {

    // date는 학교 페이지 표기 그대로("08.31( 월 )")라 "MM.dd" 접두사로 날짜를 찾는다
    boolean existsByDateStartingWithAndMenuCategory(String datePrefix, MenuCategory menuCategory);
}