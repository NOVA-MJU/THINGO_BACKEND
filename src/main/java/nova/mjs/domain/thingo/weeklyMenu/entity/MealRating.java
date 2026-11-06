package nova.mjs.domain.thingo.weeklyMenu.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import nova.mjs.util.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 학식 끼니별 평점(1~5).
 * weekly_menu 행은 크롤링 때마다 전부 지우고 다시 저장하므로 FK 대신 (날짜, 끼니)로 끼니를 식별한다.
 * (member, 날짜, 끼니) 유니크 제약으로 한 사람이 한 끼니에 평점 하나만 갖게 하고, 다시 남기면 점수를 덮어쓴다.
 */
@Entity
@Table(
        name = "meal_rating",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_meal_rating_member_meal",
                columnNames = {"member_id", "meal_date", "menu_category"}
        ),
        indexes = @Index(name = "idx_meal_rating_meal", columnList = "meal_date, menu_category")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MealRating extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_category", nullable = false)
    private MenuCategory menuCategory;

    @Column(nullable = false)
    private int score;

    public static MealRating create(Member member, LocalDate mealDate, MenuCategory menuCategory, int score) {
        MealRating rating = new MealRating();
        rating.member = member;
        rating.mealDate = mealDate;
        rating.menuCategory = menuCategory;
        rating.score = score;
        return rating;
    }

    public void changeScore(int score) {
        this.score = score;
    }
}
