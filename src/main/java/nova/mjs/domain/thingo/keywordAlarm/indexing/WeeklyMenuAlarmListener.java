package nova.mjs.domain.thingo.keywordAlarm.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.service.CafeteriaAlarmService;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmSender;
import nova.mjs.domain.thingo.weeklyMenu.event.WeeklyMenuCrawledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 학식 크롤 성공 이벤트를 받아 '학식' 구독자에게 방송 알림을 보낸다.
 *
 * - 크롤 트랜잭션 커밋 후(AFTER_COMMIT) 동작 -> 저장된 식단이 실제 반영된 뒤 알림.
 * - 매칭/내역저장은 CafeteriaAlarmService 트랜잭션에서 끝내고, FCM 은 그 뒤 비동기로 발송.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyMenuAlarmListener {

    private final CafeteriaAlarmService cafeteriaAlarmService;
    private final FcmSender fcmSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WeeklyMenuCrawledEvent event) {
        if (event == null) {
            return;
        }
        try {
            // 1. 변경 감지 + 구독자 내역 적재(트랜잭션 내부)
            List<FcmDispatch> dispatches =
                    cafeteriaAlarmService.broadcastIfNew(event.menuCount(), event.contentSignature());

            // 2. FCM 발송(트랜잭션 밖, 비동기)
            dispatches.forEach(fcmSender::sendAll);
        } catch (Exception e) {
            log.error("[학식알림] 방송 실패 - menuCount={}", event.menuCount(), e);
        }
    }
}
