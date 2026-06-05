package nova.mjs.domain.thingo.dday.dto;

import lombok.*;
import nova.mjs.domain.thingo.calendar.dto.MjuCalendarDTO;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DDayDto {

    // 디데이 진행 단계
    public enum Phase {
        UPCOMING, // 시작 전 (시작일까지 카운트다운)
        ONGOING   // 진행 중 (종료일까지 카운트다운)
    }

    public enum SourceType {
        CALENDAR  // 학사일정. 추후 NOTICE 확장 대비
    }

    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Response {
        private long ddayValue;          // 남은 일수 (target - today)
        private Phase phase;             // UPCOMING / ONGOING
        private LocalDate targetDate;    // 카운트다운 대상일 (UPCOMING=시작일, ONGOING=종료일)
        private LocalDate startDate;
        private LocalDate endDate;
        private String eventName;        // 원본 이벤트명
        private String eventNameTruncated; // 12자(공백포함) 초과 시 말줄임
        private SourceType sourceType;

        // 이벤트명 최대 글자수(말줄임표 포함)
        private static final int MAX_NAME_LENGTH = 12;
        private static final String ELLIPSIS = "…";

        // 학사일정 DTO + 기준일(today)로 디데이 응답 생성
        public static Response fromCalendar(MjuCalendarDTO calendar, LocalDate today) {
            LocalDate startDate = calendar.getStartDate();
            LocalDate endDate = calendar.getEndDate();

            // 시작 전이면 시작일, 진행 중이면 종료일을 카운트다운 대상으로
            Phase phase;
            LocalDate targetDate;
            if (today.isBefore(startDate)) {
                phase = Phase.UPCOMING;
                targetDate = startDate;
            } else {
                phase = Phase.ONGOING;
                targetDate = endDate;
            }

            long ddayValue = ChronoUnit.DAYS.between(today, targetDate);
            String eventName = calendar.getDescription() == null ? "" : calendar.getDescription();

            return Response.builder()
                    .ddayValue(ddayValue)
                    .phase(phase)
                    .targetDate(targetDate)
                    .startDate(startDate)
                    .endDate(endDate)
                    .eventName(eventName)
                    .eventNameTruncated(truncate(eventName))
                    .sourceType(SourceType.CALENDAR)
                    .build();
        }

        // 12자(공백포함) 초과 시 11자 + 말줄임표(총 12자)로 자름
        private static String truncate(String name) {
            if (name.length() <= MAX_NAME_LENGTH) {
                return name;
            }
            return name.substring(0, MAX_NAME_LENGTH - 1) + ELLIPSIS;
        }
    }
}
