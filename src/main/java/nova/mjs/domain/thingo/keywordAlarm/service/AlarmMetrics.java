package nova.mjs.domain.thingo.keywordAlarm.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 알림 파이프라인 관측 지표.
 *
 * 알림이 두 달간 0건이었는데도 아무도 몰랐던 이유는 실패가 로그에만 남았기 때문이다.
 * 발송량과 실패를 카운터로 노출해 "오늘 발송 0건" 같은 조건으로 경보를 걸 수 있게 한다.
 *
 * 지표:
 *  - keyword_alarm_dispatch_total      발송 단위(회원 1명분) 수
 *  - keyword_alarm_failure_total{stage} 파이프라인 실패 (stage=match/listener/cafeteria)
 *  - fcm_send_total{result,code}        FCM 단말 발송 결과 (code=성공 시 none)
 */
@Component
public class AlarmMetrics {

    private static final String DISPATCH = "keyword_alarm_dispatch";
    private static final String FAILURE = "keyword_alarm_failure";
    private static final String FCM_SEND = "fcm_send";

    private final MeterRegistry registry;
    private final Counter dispatch;

    public AlarmMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.dispatch = Counter.builder(DISPATCH)
                .description("키워드/학식 알림 발송 단위 수")
                .register(registry);
    }

    public void dispatched(int count) {
        if (count > 0) {
            dispatch.increment(count);
        }
    }

    /** 매칭/내역 저장 실패 */
    public void matchFailed() {
        failed("match");
    }

    /** stage 별 실패 (listener/cafeteria/backfill 등) */
    public void failed(String stage) {
        registry.counter(FAILURE, "stage", stage).increment();
    }

    public void fcmSent() {
        registry.counter(FCM_SEND, "result", "success", "code", "none").increment();
    }

    public void fcmFailed(String errorCode) {
        registry.counter(FCM_SEND, "result", "fail",
                "code", errorCode == null ? "unknown" : errorCode).increment();
    }
}
