package nova.mjs.domain.thingo.keywordAlarm.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.service.AlarmMetrics;
import nova.mjs.domain.thingo.keywordAlarm.service.KeywordMatchingService;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmSender;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 실시간 이벤트를 놓친 콘텐츠의 알림을 뒤늦게 채운다.
 *
 * 색인은 두 경로로 채워진다.
 *  - 실시간: EntityIndexEvent -> KeywordAlarmIndexListener (알림 발화)
 *  - 보정:   PgSearchIndexSyncService.reconcile (이벤트 없음 -> 알림 누락)
 * 후자로 들어온 콘텐츠는 검색에는 뜨지만 알림이 영영 나가지 않으므로, 최근 색인분을 다시 매칭에 태운다.
 *
 * 중복 발송은 기존 dedup(Redis claim + (회원, 콘텐츠) 유일 제약)이 막는다.
 * 오래된 게시물이 한꺼번에 소급 발송되지 않도록 콘텐츠 날짜 하한(CONTENT_MAX_AGE)을 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissedAlarmBackfillService {

    /** 색인 시각 조회 창. 하루 1회 실행 + 여유분. */
    private static final Duration INDEXED_WINDOW = Duration.ofHours(26);

    /** 콘텐츠 자체가 이보다 오래되면 건너뛴다(뒤늦은 대량 발송 방지). */
    private static final Duration CONTENT_MAX_AGE = Duration.ofDays(7);

    /** 한 번에 처리할 최대 건수(사고 시 폭발 방지). */
    private static final int MAX_ROWS = 300;

    private final UnifiedSearchIndexRepository unifiedSearchIndexRepository;
    private final KeywordMatchingService keywordMatchingService;
    private final FcmSender fcmSender;
    private final AlarmMetrics alarmMetrics;

    public int backfill() {
        Instant now = Instant.now();
        List<UnifiedSearchIndex> candidates = unifiedSearchIndexRepository.findAlarmBackfillCandidates(
                alarmSearchTypes(),
                now.minus(INDEXED_WINDOW),
                now.minus(CONTENT_MAX_AGE),
                MAX_ROWS);

        int dispatched = 0;
        for (UnifiedSearchIndex row : candidates) {
            try {
                List<FcmDispatch> dispatches = keywordMatchingService.matchAndCollect(new IndexedDocument(row));
                dispatches.forEach(fcmSender::sendAll);
                dispatched += dispatches.size();
            } catch (Exception e) {
                alarmMetrics.failed("backfill");
                log.error("[알림backfill] 처리 실패 - id={}", row.getId(), e);
            }
        }

        log.info("[알림backfill] 후보 {}건 중 발송 단위 {}건", candidates.size(), dispatched);
        return dispatched;
    }

    /** 학식(WEEKLY_MENU)은 인덱스에 없으므로 자연히 빠진다. */
    private List<String> alarmSearchTypes() {
        return Arrays.stream(AlarmCategory.values())
                .filter(category -> category != AlarmCategory.CAFETERIA)
                .map(AlarmCategory::getSearchType)
                .toList();
    }

    /**
     * 인덱스 행을 매칭 입력(SearchDocument)으로 바꾼다.
     *
     * getId 는 원본 id 를 돌려준다. 매칭 쪽에서 "type:원본id" 로 searchIndexId 를 다시 만들기 때문에
     * 인덱스 행의 id("NOTICE:2902")를 그대로 주면 접두가 두 번 붙어 dedup 키가 어긋난다.
     */
    private record IndexedDocument(UnifiedSearchIndex row) implements SearchDocument {

        @Override
        public String getId() {
            return row.getOriginalId();
        }

        @Override
        public String getTitle() {
            return row.getTitle();
        }

        @Override
        public String getContent() {
            return row.getContent();
        }

        @Override
        public String getType() {
            return row.getType();
        }

        @Override
        public Instant getInstant() {
            return row.getDate();
        }

        @Override
        public String getLink() {
            return row.getLink();
        }

        @Override
        public String getCategory() {
            return row.getCategory();
        }
    }
}
