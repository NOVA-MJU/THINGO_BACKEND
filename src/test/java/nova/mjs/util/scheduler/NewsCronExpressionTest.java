package nova.mjs.util.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

/**
 * 뉴스 스케줄러 cron 표현식 동작 검증.
 * 기존(버그) vs 수정 후 다음 실행 시각을 실제로 계산해 비교한다.
 */
class NewsCronExpressionTest {

    @Test
    void compareOldVsNewCron() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 16, 0, 0); // 오늘(6월) 기준

        CronExpression buggy = CronExpression.parse("0 0 * * 1 *");   // month=1 -> 1월에만
        CronExpression fixed = CronExpression.parse("0 0 9 * * MON"); // 매주 월 09:00

        System.out.println("=== 기존 '0 0 * * 1 *' (6월 기준 다음 5회) ===");
        printNext(buggy, base, 5);

        System.out.println("=== 수정 '0 0 9 * * MON' (6월 기준 다음 5회) ===");
        printNext(fixed, base, 5);
    }

    private void printNext(CronExpression cron, LocalDateTime base, int count) {
        LocalDateTime t = base;
        for (int i = 0; i < count; i++) {
            t = cron.next(t);
            if (t == null) {
                System.out.println("  (다음 실행 없음)");
                return;
            }
            System.out.printf("  %s (%s)%n", t, t.getDayOfWeek());
        }
    }
}
