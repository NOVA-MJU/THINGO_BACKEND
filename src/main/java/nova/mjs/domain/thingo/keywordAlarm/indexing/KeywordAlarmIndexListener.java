package nova.mjs.domain.thingo.keywordAlarm.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.ElasticSearch.indexing.event.EntityIndexEvent;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.service.KeywordMatchingService;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 신규 콘텐츠 색인 이벤트를 받아 키워드 알림을 발화한다.
 *
 * - 검색 인덱싱(PgUnifiedSearchIndexListener)과 같은 EntityIndexEvent 를 구독하되,
 *   알림 실패가 검색 색인에 영향을 주지 않도록 별도 리스너로 분리한다.
 * - INSERT(콘텐츠 최초 등장)만 알림한다. UPDATE(좋아요/댓글/수정)는 재알림하지 않는다.
 * - 매칭/내역저장은 KeywordMatchingService 의 트랜잭션에서 끝내고,
 *   FCM 발송은 그 뒤에 비동기로 호출한다(외부 호출을 트랜잭션 밖으로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordAlarmIndexListener {

    private final KeywordMatchingService keywordMatchingService;
    private final FcmSender fcmSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EntityIndexEvent<? extends SearchDocument> event) {
        if (event == null || event.getDocument() == null || event.getAction() == null) {
            return;
        }
        // 최초 등장만 알림
        if (event.getAction() != EntityIndexEvent.IndexAction.INSERT) {
            return;
        }

        SearchDocument doc = event.getDocument();
        // MVP 3개 카테고리(공지/학사일정/게시판) 외 타입은 즉시 제외
        if (AlarmCategory.fromSearchType(doc.getType()).isEmpty()) {
            return;
        }

        try {
            // 1. 매칭 + 내역 적재(트랜잭션 내부)
            List<FcmDispatch> dispatches = keywordMatchingService.matchAndCollect(doc);

            // 2. FCM 발송(트랜잭션 밖, 비동기)
            dispatches.forEach(fcmSender::sendAll);

            if (!dispatches.isEmpty()) {
                log.info("[키워드알림] 발송 단위 {}건 - type={}, id={}", dispatches.size(), doc.getType(), doc.getId());
            }
        } catch (Exception e) {
            log.error("[키워드알림] 매칭/발송 실패 - type={}, id={}", doc.getType(), doc.getId(), e);
        }
    }
}
