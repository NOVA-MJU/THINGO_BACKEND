package nova.mjs.domain.thingo.weeklyMenu.repository;

import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.weeklyMenu.entity.MealRating;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealRatingRepository extends JpaRepository<MealRating, Long> {

    Optional<MealRating> findByMemberAndMealDateAndMenuCategory(
            Member member, LocalDate mealDate, MenuCategory menuCategory);

    List<MealRating> findByMemberAndMealDate(Member member, LocalDate mealDate);

    @Query("""
            select r.menuCategory as menuCategory, avg(r.score) as average, count(r) as count
            from MealRating r
            where r.mealDate = :mealDate
            group by r.menuCategory
            """)
    List<Summary> summarize(@Param("mealDate") LocalDate mealDate);

    interface Summary {
        MenuCategory getMenuCategory();
        Double getAverage();
        Long getCount();
    }
}
