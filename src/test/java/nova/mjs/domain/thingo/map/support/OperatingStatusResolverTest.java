package nova.mjs.domain.thingo.map.support;

import nova.mjs.domain.thingo.map.entity.OperatingHour;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 상태 계산 + 다음 오픈 안내 라벨 단위 테스트.
 * 실행 시각에 의존하지 않도록 now를 직접 주입하고, 요일은 now 기준으로 상대 구성한다.
 */
class OperatingStatusResolverTest {

    private final OperatingStatusResolver resolver = new OperatingStatusResolver();

    @Test
    @DisplayName("운영 종료 상태면 다음 오픈 시각을 라벨에 덧붙인다")
    void should_appendNextOpen_when_closed() {
        // given - 오늘 09:00~18:00, 내일 10:00~17:00. 현재는 오늘 20:00(마감 후)
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 20, 0);
        DayOfWeek today = now.getDayOfWeek();
        DayOfWeek tomorrow = today.plus(1);
        List<OperatingHour> hours = List.of(
                OperatingHour.ofOpen(null, today, LocalTime.of(9, 0), LocalTime.of(18, 0), null),
                OperatingHour.ofOpen(null, tomorrow, LocalTime.of(10, 0), LocalTime.of(17, 0), null)
        );

        // when
        LocalDateTime nextOpen = resolver.resolveNextOpen(hours, now);
        String label = resolver.resolveDisplayLabel(hours, now);

        // then - 다음 오픈은 내일 10:00, 라벨에 "내일 10:00 오픈"이 붙는다
        assertThat(nextOpen).isEqualTo(now.plusDays(1).toLocalDate().atTime(10, 0));
        assertThat(label).isEqualTo("운영 종료 (내일 10:00 오픈)");
    }

    @Test
    @DisplayName("운영중이면 기본 라벨을 그대로 쓴다")
    void should_returnOpenLabel_when_open() {
        // given - 오늘 09:00~18:00, 현재는 오늘 12:00
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);
        List<OperatingHour> hours = List.of(
                OperatingHour.ofOpen(null, now.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(18, 0), null)
        );

        // when - then
        assertThat(resolver.resolveDisplayLabel(hours, now)).isEqualTo("운영중");
    }

    @Test
    @DisplayName("오늘이 휴무면 휴무 라벨")
    void should_returnHoliday_when_closedToday() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);
        List<OperatingHour> hours = List.of(OperatingHour.ofClosed(null, now.getDayOfWeek()));

        // when - then
        assertThat(resolver.resolveDisplayLabel(hours, now)).isEqualTo("휴무");
    }

    @Test
    @DisplayName("운영시간 데이터가 없으면 라벨은 null (상태 미표시)")
    void should_returnNull_when_noHours() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);

        // when - then
        assertThat(resolver.resolveDisplayLabel(List.of(), now)).isNull();
    }
}
