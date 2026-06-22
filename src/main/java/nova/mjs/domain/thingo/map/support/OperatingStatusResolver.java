package nova.mjs.domain.thingo.map.support;

import nova.mjs.domain.thingo.map.entity.OperatingHour;
import nova.mjs.domain.thingo.map.entity.OperatingStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
}
