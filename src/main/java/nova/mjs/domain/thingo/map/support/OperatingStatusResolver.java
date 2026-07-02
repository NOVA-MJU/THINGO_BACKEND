package nova.mjs.domain.thingo.map.support;

import nova.mjs.domain.thingo.map.entity.OperatingHour;
import nova.mjs.domain.thingo.map.entity.OperatingStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 운영시간 + 현재 시각으로 운영 상태(운영중/곧 종료 등)를 계산한다.
 *
 * 운영 상태는 저장하지 않고 매 요청마다 여기서 산출한다.
 * 운영시간 데이터가 없는 핀(예: 미입력 외부 맛집)은 null을 반환해 상태를 표시하지 않는다.
 */
@Component
public class OperatingStatusResolver {

    /** '곧 운영 시작/종료' 판정 임계값 (분) */
    private static final int SOON_THRESHOLD_MINUTES = 30;

    /**
     * 현재 운영 상태를 계산한다.
     *
     * @param operatingHours 핀의 요일별 운영시간 (없으면 빈 리스트)
     * @param now            현재 시각 (호출 측에서 KST 기준으로 전달)
     * @return 운영 상태. 운영시간 데이터가 전혀 없으면 null
     */
    public OperatingStatus resolve(List<OperatingHour> operatingHours, LocalDateTime now) {
        // 1. 운영시간 데이터 자체가 없으면 상태 미표시
        if (operatingHours == null || operatingHours.isEmpty()) {
            return null;
        }

        // 2. 오늘 요일의 운영시간을 찾는다. 없으면 오늘은 휴무로 본다
        DayOfWeek today = now.getDayOfWeek();
        Optional<OperatingHour> todayHour = operatingHours.stream()
                .filter(hour -> hour.getDayOfWeek() == today)
                .findFirst();
        if (todayHour.isEmpty()) {
            return OperatingStatus.HOLIDAY;
        }

        OperatingHour hour = todayHour.get();

        // 3. 명시적 휴무 / 24시간 운영 우선 처리
        if (hour.isClosed()) {
            return OperatingStatus.HOLIDAY;
        }
        if (hour.isAlways24h()) {
            return OperatingStatus.ALWAYS_OPEN;
        }

        // 4. 운영 시작/종료 시각이 비어있으면 상태를 판단할 수 없음
        if (hour.getOpenTime() == null || hour.getCloseTime() == null) {
            return null;
        }

        // 5. 현재 시각을 '자정 기준 분'으로 환산해 구간 비교
        int nowMinutes = minutesOfDay(now.toLocalTime());
        int openMinutes = minutesOfDay(hour.getOpenTime());
        int closeMinutes = minutesOfDay(hour.getCloseTime());
        int preOpenStart = openMinutes - SOON_THRESHOLD_MINUTES;   // 곧 운영 시작 구간 시작
        int preCloseStart = closeMinutes - SOON_THRESHOLD_MINUTES; // 곧 운영 종료 구간 시작

        if (nowMinutes < preOpenStart || nowMinutes >= closeMinutes) {
            return OperatingStatus.CLOSED;        // 영업 시간 외
        }
        if (nowMinutes < openMinutes) {
            return OperatingStatus.PRE_OPEN;      // 운영 시작 30분 전
        }
        if (nowMinutes < preCloseStart) {
            return OperatingStatus.OPEN;          // 운영중
        }
        return OperatingStatus.PRE_CLOSE;         // 운영 종료 30분 전
    }

    private int minutesOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** 요일 한글 라벨 (월~일). DayOfWeek.getValue() 1(월)~7(일) 대응 */
    private static final String[] KOREAN_DAY = {"월", "화", "수", "목", "금", "토", "일"};

    /**
     * 검색 카드용 운영 상태 표시 라벨을 만든다.
     * '운영 종료(CLOSED)'일 때는 다음 오픈 시각을 덧붙인다. (예: "운영 종료 (내일 09:00 오픈)")
     * 나머지 상태는 기본 라벨을 그대로 쓰고, 운영시간 데이터가 없으면 null.
     */
    public String resolveDisplayLabel(List<OperatingHour> operatingHours, LocalDateTime now) {
        OperatingStatus status = resolve(operatingHours, now);
        if (status == null) {
            return null;
        }
        if (status != OperatingStatus.CLOSED) {
            return status.getLabel();
        }
        LocalDateTime nextOpen = resolveNextOpen(operatingHours, now);
        if (nextOpen == null) {
            return status.getLabel();
        }
        return status.getLabel() + " (" + formatNextOpen(now, nextOpen) + " 오픈)";
    }

    /**
     * 다음 오픈 일시를 계산한다. (현재 운영 종료 상태일 때만 의미가 있다)
     * 오늘부터 최대 7일 뒤(다음 주 같은 요일)까지 훑어 현재 시각 이후 첫 오픈 시각을 찾는다.
     * 앞으로 7일 안에 오픈이 없으면(전부 휴무/미입력) null.
     *
     * @param operatingHours 요일별 운영시간
     * @param now            현재 시각 (KST)
     */
    public LocalDateTime resolveNextOpen(List<OperatingHour> operatingHours, LocalDateTime now) {
        if (operatingHours == null || operatingHours.isEmpty()) {
            return null;
        }
        Map<DayOfWeek, OperatingHour> byDay = new EnumMap<>(DayOfWeek.class);
        for (OperatingHour hour : operatingHours) {
            byDay.put(hour.getDayOfWeek(), hour);
        }

        for (int offset = 0; offset <= 7; offset++) {
            LocalDateTime day = now.plusDays(offset);
            OperatingHour hour = byDay.get(day.getDayOfWeek());
            if (hour == null || hour.isClosed()) {
                continue;                                   // 휴무/미입력 요일은 건너뜀
            }
            if (hour.isAlways24h()) {
                if (offset == 0) {
                    continue;                               // 오늘 24시간이면 CLOSED로 오지 않음 (방어)
                }
                return day.toLocalDate().atStartOfDay();    // 미래 요일의 24시간 = 자정 오픈
            }
            if (hour.getOpenTime() == null) {
                continue;
            }
            LocalDateTime openAt = day.toLocalDate().atTime(hour.getOpenTime());
            if (openAt.isAfter(now)) {
                return openAt;                              // 현재 이후 첫 오픈
            }
        }
        return null;
    }

    /** 다음 오픈 시각을 "오늘/내일/요일 + HH:mm" 형식으로 표현 */
    private String formatNextOpen(LocalDateTime now, LocalDateTime nextOpen) {
        String time = String.format("%02d:%02d", nextOpen.getHour(), nextOpen.getMinute());
        long dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), nextOpen.toLocalDate());
        if (dayDiff == 0) {
            return "오늘 " + time;
        }
        if (dayDiff == 1) {
            return "내일 " + time;
        }
        return KOREAN_DAY[nextOpen.getDayOfWeek().getValue() - 1] + " " + time;
    }
}
