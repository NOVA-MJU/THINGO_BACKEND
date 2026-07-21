package nova.mjs.domain.thingo.keywordAlarm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 키워드 알림 구독 요청/응답 DTO.
 * 검증 메시지는 화면 설계서(06-2-3, 5번 조건)의 안내 문구를 그대로 사용한다.
 */
public class KeywordSubscriptionDTO {

    public static class Request {

        /** 키워드 등록 요청 */
        @Getter
        @NoArgsConstructor
        public static class Create {

            @NotBlank(message = "올바른 형식의 키워드를 입력해 주세요.")
            @Size(min = 1, max = 5, message = "올바른 형식의 키워드를 입력해 주세요.")
            @Pattern(regexp = "^\\S+$", message = "올바른 형식의 키워드를 입력해 주세요.") // 공백 제외
            private String keyword;

            @NotEmpty(message = "알림 카테고리를 1개 이상 선택해 주세요.")
            private Set<AlarmCategory> categories;
        }

        /** 구독 수정 요청 (키워드 + 카테고리 전체 교체) */
        @Getter
        @NoArgsConstructor
        public static class Update {

            @NotBlank(message = "올바른 형식의 키워드를 입력해 주세요.")
            @Size(min = 1, max = 5, message = "올바른 형식의 키워드를 입력해 주세요.")
            @Pattern(regexp = "^\\S+$", message = "올바른 형식의 키워드를 입력해 주세요.") // 공백 제외
            private String keyword;

            @NotEmpty(message = "알림 카테고리를 1개 이상 선택해 주세요.")
            private Set<AlarmCategory> categories;
        }

        /** 알림 on/off 수정 요청 */
        @Getter
        @NoArgsConstructor
        public static class UpdateEnabled {

            @NotNull(message = "알림 활성 여부(enabled)를 지정해 주세요.")
            private Boolean enabled;
        }
    }

    public static class Response {

        @Getter
        @Builder
        public static class Detail {
            private final Long id;
            private final String keyword;
            private final Set<AlarmCategory> categories;
            private final boolean enabled;
            private final LocalDateTime createdAt;

            public static Detail from(KeywordSubscription subscription) {
                return Detail.builder()
                        .id(subscription.getId())
                        .keyword(subscription.getKeyword())
                        .categories(new LinkedHashSet<>(subscription.getCategories()))
                        .enabled(subscription.isEnabled())
                        .createdAt(subscription.getCreatedAt())
                        .build();
            }
        }
    }
}
