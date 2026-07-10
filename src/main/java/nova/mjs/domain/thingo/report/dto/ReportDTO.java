package nova.mjs.domain.thingo.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.report.entity.Report;
import nova.mjs.domain.thingo.report.entity.ReportReason;
import nova.mjs.domain.thingo.report.entity.ReportTargetType;

import java.time.LocalDateTime;
import java.util.UUID;

public class ReportDTO {

    public static class Request {

        /** 신고 접수 요청 */
        @Getter
        @NoArgsConstructor
        public static class Create {

            @NotNull(message = "신고 대상 유형은 필수입니다.")
            private ReportTargetType targetType;

            @NotNull(message = "신고 대상 식별자는 필수입니다.")
            private UUID targetUuid;

            @NotNull(message = "신고 사유는 필수입니다.")
            private ReportReason reason;

            /** 기타 사유 상세 (reason=ETC일 때 필수, 최대 400자) */
            @Size(max = 400, message = "기타 사유는 최대 400자까지 입력 가능합니다.")
            private String etcDetail;
        }
    }

    public static class Response {

        /** 신고 접수 결과 */
        @Getter
        @Builder
        public static class Detail {
            private UUID reportUuid;
            private ReportTargetType targetType;
            private UUID targetUuid;
            private ReportReason reason;
            private String reasonLabel;   // 신고 사유 한글 항목명
            private String etcDetail;
            private LocalDateTime createdAt;
            private String message;       // 신고 완료 안내 문구

            public static Detail from(Report report) {
                return Detail.builder()
                        .reportUuid(report.getUuid())
                        .targetType(report.getTargetType())
                        .targetUuid(report.getTargetUuid())
                        .reason(report.getReason())
                        .reasonLabel(report.getReason().getLabel())
                        .etcDetail(report.getEtcDetail())
                        .createdAt(report.getCreatedAt())
                        .message("신고가 정상적으로 접수되었습니다.")
                        .build();
            }
        }
    }
}
