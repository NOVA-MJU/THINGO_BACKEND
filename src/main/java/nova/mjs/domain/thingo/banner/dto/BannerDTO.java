package nova.mjs.domain.thingo.banner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.banner.entity.Banner;

import java.util.List;
import java.util.UUID;

/**
 * 배너 DTO. 도메인당 1개 클래스, Request/Response는 inner class.
 */
public class BannerDTO {

    /* ==========================================================
     * 동기화 수신 (구글 시트 → 백엔드 webhook)
     * ========================================================== */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {

        /** 시트 전체 행 (헤더 제외) */
        @NotNull
        @Valid
        private List<SyncRow> rows;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncRow {

        @NotBlank
        private String title;

        private String oneLineIntro;

        private String imageUrl;

        private String category;

        /** 배너 클릭 시 이동 URL */
        private String linkUrl;

        /** 노출 순서 (시트 행 순서 대용). null이면 0으로 처리 */
        private Integer displayOrder;

        /** 노출 여부. null이면 true로 처리 */
        private Boolean active;

        /** 노출 시작일 "yyyy-MM-dd" (빈 값이면 제한 없음) */
        private String startAt;

        /** 노출 종료일 "yyyy-MM-dd" (빈 값이면 제한 없음) */
        private String endAt;
    }

    /* ==========================================================
     * 동기화 결과 응답
     * ========================================================== */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class SyncResult {
        private int syncedCount;
    }

    /* ==========================================================
     * 공개 조회 응답 (앱)
     * ========================================================== */
    @Getter
    @Builder
    public static class Response {
        private UUID uuid;
        private String title;
        private String oneLineIntro;
        private String imageUrl;
        private String category;
        private String linkUrl;
        private int displayOrder;

        public static Response from(Banner banner) {
            return Response.builder()
                    .uuid(banner.getUuid())
                    .title(banner.getTitle())
                    .oneLineIntro(banner.getOneLineIntro())
                    .imageUrl(banner.getImageUrl())
                    .category(banner.getCategory())
                    .linkUrl(banner.getLinkUrl())
                    .displayOrder(banner.getDisplayOrder())
                    .build();
        }
    }
}
