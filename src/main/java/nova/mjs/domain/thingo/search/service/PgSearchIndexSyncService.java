package nova.mjs.domain.thingo.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.Document.BroadcastDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.CommunityDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.DepartmentScheduleDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.MjuCalendarDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.NewsDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.NoticeDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.ElasticSearch.Document.StudentCouncilNoticeDocument;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.community.CommunityContentPreprocessor;
import nova.mjs.domain.thingo.ElasticSearch.indexing.Preprocessor.notice.NoticeContentPreprocessor;
import nova.mjs.domain.thingo.broadcast.repository.BroadcastRepository;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import nova.mjs.domain.thingo.community.repository.CommunityBoardRepository;
import nova.mjs.domain.thingo.department.repository.DepartmentScheduleRepository;
import nova.mjs.domain.thingo.department.repository.StudentCouncilNoticeRepository;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.mapper.PgUnifiedSearchMapper;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * RDB -> PostgreSQL 통합 인덱스 동기화.
 *
 * 두 가지 동기화 경로를 제공한다.
 *  - syncAll(): 전체 truncate + 재적재. 운영자 수동 호출(POST /sync)용. 재적재 중 짧은 빈 결과 구간 발생.
 *  - reconcile(): truncate 없이 소스와 인덱스를 diff 하여 변경분만 upsert + 사라진 건 deactivate.
 *                 야간 스케줄러가 호출하는 무중단 정합성 보정 경로.
 *
 * 평상시 실시간 반영은 PgUnifiedSearchIndexListener(AFTER_COMMIT)가 담당하고,
 * reconcile 은 이벤트 누락으로 생긴 drift 를 주기적으로 메운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgSearchIndexSyncService {

    private static final int BATCH_SIZE = 1000;

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final CommunityBoardRepository communityBoardRepository;
    private final DepartmentScheduleRepository departmentScheduleRepository;
    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    private final BroadcastRepository broadcastRepository;
    private final MjuCalendarRepository mjuCalendarRepository;

    private final NoticeContentPreprocessor noticeContentPreprocessor;
    private final CommunityContentPreprocessor communityContentPreprocessor;

    private final UnifiedSearchIndexRepository repository;
    private final PgUnifiedSearchMapper mapper;

    /**
     * 전체 재구축(운영 전용). 인덱스를 비우고 소스 전체를 다시 적재한다.
     */
    @Transactional
    public void syncAll() {
        log.info("[PgSearch][SYNC] start");

        repository.truncate();

        List<UnifiedSearchIndex> desired = collectAll();
        for (int i = 0; i < desired.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, desired.size());
            repository.saveAll(desired.subList(i, end));
        }

        log.info("[PgSearch][SYNC] end. indexed={}", desired.size());
    }

    /**
     * 무중단 정합성 보정.
     *
     * 소스가 만드는 "원하는 인덱스 상태"와 현재 인덱스를 비교한다.
     *  - 신규/변경 문서 → upsert
     *  - 소스에서 사라진 active 문서 → deactivate (물리 삭제 아님, 안전)
     * truncate 를 하지 않으므로 보정 중에도 검색 결과가 비지 않는다.
     */
    @Transactional
    public void reconcile() {
        log.info("[PgSearch][RECONCILE] start");

        // 1) 소스 기준 "원하는 상태" 구성 (id -> 문서)
        Map<String, UnifiedSearchIndex> desired = new HashMap<>();
        for (UnifiedSearchIndex doc : collectAll()) {
            desired.put(doc.getId(), doc);
        }

        int updated = 0;
        int deactivated = 0;
        int unchanged = 0;

        // 2) 기존 인덱스를 순회하며 변경분 갱신 + 사라진 건 비활성.
        //    처리한 id 는 desired 에서 제거 → 남은 것이 신규 문서.
        for (UnifiedSearchIndex existing : repository.findAll()) {
            UnifiedSearchIndex want = desired.remove(existing.getId());
            if (want == null) {
                if (Boolean.TRUE.equals(existing.getActive())) {
                    existing.deactivate();
                    deactivated++;
                }
                continue;
            }
            if (existing.differsFrom(want)) {
                existing.updateFrom(want);
                updated++;
            } else {
                unchanged++;
            }
        }

        // 3) 인덱스에 없던 신규 문서 저장.
        int inserted = desired.size();
        if (!desired.isEmpty()) {
            List<UnifiedSearchIndex> news = new ArrayList<>(desired.values());
            for (int i = 0; i < news.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, news.size());
                repository.saveAll(news.subList(i, end));
            }
        }

        log.info("[PgSearch][RECONCILE] end. new={}, updated={}, deactivated={}, unchanged={}",
                inserted, updated, deactivated, unchanged);
    }

    /**
     * 모든 도메인 소스를 인덱스 문서로 변환해 모은다(link 중복 제거).
     *
     * 동일 link 중복 차단: 원본 공지가 여러 게시판에 동시 게재되면 notice 테이블엔
     * 서로 다른 row(originalId 다름)이지만 link 는 같다. 검색 중복 노출을 여기서 막는다.
     */
    private List<UnifiedSearchIndex> collectAll() {
        Set<String> seenLinks = new HashSet<>();
        List<UnifiedSearchIndex> out = new ArrayList<>();

        collect(out, "NOTICE",
                noticeRepository.findAll(),
                e -> NoticeDocument.from(e, noticeContentPreprocessor),
                seenLinks);

        collect(out, "COMMUNITY",
                communityBoardRepository.findAll(),
                e -> CommunityDocument.from(e, communityContentPreprocessor),
                seenLinks);

        collect(out, "NEWS",
                newsRepository.findAll(),
                NewsDocument::from,
                seenLinks);

        collect(out, "DEPARTMENT_SCHEDULE",
                departmentScheduleRepository.findAll(),
                DepartmentScheduleDocument::from,
                seenLinks);

        collect(out, "STUDENT_COUNCIL_NOTICE",
                studentCouncilNoticeRepository.findAll(),
                StudentCouncilNoticeDocument::from,
                seenLinks);

        collect(out, "BROADCAST",
                broadcastRepository.findAll(),
                BroadcastDocument::from,
                seenLinks);

        collect(out, "MJU_CALENDAR",
                mjuCalendarRepository.findAll(),
                MjuCalendarDocument::from,
                seenLinks);

        return out;
    }

    private <E> void collect(List<UnifiedSearchIndex> out,
                             String domain,
                             List<E> entities,
                             Function<E, ? extends SearchDocument> toDocument,
                             Set<String> seenLinks) {
        if (entities == null || entities.isEmpty()) {
            log.info("[PgSearch][COLLECT][{}] empty", domain);
            return;
        }

        int added = 0;
        int skipped = 0;
        for (E entity : entities) {
            SearchDocument doc;
            try {
                doc = toDocument.apply(entity);
            } catch (Exception e) {
                log.warn("[PgSearch][COLLECT][{}] doc convert failed", domain, e);
                continue;
            }
            if (doc == null) continue;

            // link 가 있는 경우에만 dedupe. null/blank link 는 dedupe 대상에서 제외(구분 불가).
            String link = doc.getLink();
            if (link != null && !link.isBlank() && !seenLinks.add(link)) {
                skipped++;
                continue;
            }

            out.add(mapper.from(doc));
            added++;
        }

        log.info("[PgSearch][COLLECT][{}] added={} dedup_skipped={}", domain, added, skipped);
    }
}
