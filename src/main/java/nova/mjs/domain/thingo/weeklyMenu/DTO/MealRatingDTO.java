package nova.mjs.domain.thingo.weeklyMenu.DTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;

public class MealRatingDTO {

    public static class Request {

        /** 오늘 끼니에 평점 남기기. 날짜는 서버가 KST 오늘로 정해 과거·미래 끼니 평점을 막는다 */
        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Rate {

            @NotNull
            private MenuCategory menuCategory;

            @NotNull
            @Min(1)
            @Max(5)
            private Integer score;
        }
    }

    public static class Response {

        /** 끼니별 평균(소수 첫째 자리 반올림)·참여 수·내 점수(비로그인 또는 미참여면 null) */
        @Getter
        @Builder
        public static class Summary {
            private MenuCategory menuCategory;
            private double average;
            private long count;
            private Integer myScore;
        }
    }
}
