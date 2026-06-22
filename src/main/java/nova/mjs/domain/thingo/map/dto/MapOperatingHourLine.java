package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.OperatingHour;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

/**
 * 요일별 운영시간 1줄 (운영시간 토글에 줄바꿈으로 나열되는 한 행).
 *
 * 예) "월 10:00-15:00", "일 휴무", "토 24시간"
 * 운영 상태 계산용 원본과 별개로, 화면에 그대로 뿌릴 수 있는 텍스트를 만들어 내려준다.
 */
@Getter
@Builder
public class MapOperatingHourLine {

    /** 요일 식별자 (MONDAY ...) */
    private final String dayOfWeek;
    /** 요일 한글 (월/화/...) */
    private final String dayLabel;
    /** 운영시간 표시 텍스트 ("10:00-15:00" / "24시간" / "휴무") */
    private final String text;
    /** 부가 안내 (예: 학생증 태그). 없으면 null */
    private final String note;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] KOREAN_DAYS = {"월", "화", "수", "목", "금", "토", "일"};

    public static MapOperatingHourLine from(OperatingHour hour) {
        return MapOperatingHourLine.builder()
                .dayOfWeek(hour.getDayOfWeek().name())
                .dayLabel(koreanDay(hour.getDayOfWeek()))
                .text(buildText(hour))
                .note(hour.getNote())
                .build();
    }

    /** 운영시간을 화면 텍스트로 변환 (휴무/24시간/일반 시간대) */
    private static String buildText(OperatingHour hour) {
        if (hour.isClosed()) {
            return "휴무";
        }
        if (hour.isAlways24h()) {
            return "24시간";
        }
        if (hour.getOpenTime() == null || hour.getCloseTime() == null) {
            return "운영시간 정보 없음";
        }
        return hour.getOpenTime().format(TIME_FORMAT) + "-" + hour.getCloseTime().format(TIME_FORMAT);
    }

    private static String koreanDay(DayOfWeek dayOfWeek) {
        return KOREAN_DAYS[dayOfWeek.getValue() - 1];
    }
}
