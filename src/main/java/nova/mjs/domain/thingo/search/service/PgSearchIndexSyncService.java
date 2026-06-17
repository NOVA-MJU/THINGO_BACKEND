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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * RDB -> PostgreSQL 통합 인덱스 전체 동기화.
 *
 * - 도메인 인덱스를 별도로 두지 않는다 (PG 구현은 통합 인덱스 단일).
 * - 전체 truncate + 재적재 전략.
 * - 1000건 단위 flush.
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

    @Transactional
    public void syncAll() {
        log.info("[PgSearch][SYNC] start");

        repository.truncate();

        /*
         * 동일 link 중복 인덱싱 차단용 set.
         * 원본 공지가 여러 카테고리 게시판에 동시 게재되는 경우, notice 테이블에는
         * 서로 다른 row(originalId 다름)로 존재하지만 link는 같다. 검색 결과에 중복으로
         * 노출되는 것을 sync 단계에서 한 번에 막는다. 도메인 경계를 넘는 link 충돌도 함께 차단된다.
         */
        Set<String> seenLinks = new HashSet<>();

        ingest("NOTICE",
                noticeRepository.findAll(),
                e -> NoticeDocument.from(e, noticeContentPreprocessor),
                seenLinks);

        ingest("COMMUNITY",
                communityBoardRepository.findAll(),
                e -> CommunityDocument.from(e, communityContentPreprocessor),
                seenLinks);

        ingest("NEWS",
                newsRepository.findAll(),
                NewsDocument::from,
                seenLinks);

        ingest("DEPARTMENT_SCHEDULE",
                departmentScheduleRepository.findAll(),
                DepartmentScheduleDocument::from,
                seenLinks);

        ingest("STUDENT_COUNCIL_NOTICE",
                studentCouncilNoticeRepository.findAll(),
                StudentCouncilNoticeDocument::from,
                seenLinks);

        ingest("BROADCAST",
                broadcastRepository.findAll(),
                BroadcastDocument::from,
                seenLinks);

        ingest("MJU_CALENDAR",
                mjuCalendarRepository.findAll(),
                MjuCalendarDocument::from,
                seenLinks);

        log.info("[PgSearch][SYNC] end");
    }

    private <E> void ingest(String domain,
                            List<E> entities,
                            Function<E, ? extends SearchDocument> toDocument,
                            Set<String> seenLinks) {
        if (entities == null || entities.isEmpty()) {
            log.info("[PgSearch][SYNC][{}] empty", domain);
            return;
        }

        List<UnifiedSearchIndex> buffer = new ArrayList<>(BATCH_SIZE);
        int total = 0;
        int skipped = 0;

        for (E entity : entities) {
            SearchDocument doc;
            try {
                doc = toDocument.apply(entity);
            } catch (Exception e) {
                log.warn("[PgSearch][SYNC][{}] doc convert failed", domain, e);
                continue;
            }
            if (doc == null) continue;

            // link 가 있는 경우에만 dedupe. null/blank link 는 dedupe 대상에서 제외 (구분 불가).
            String link = doc.getLink();
            if (link != null && !link.isBlank()) {
                if (!seenLinks.add(link)) {
                    skipped++;
                    continue;
                }
            }

            buffer.add(mapper.from(doc));

            if (buffer.size() >= BATCH_SIZE) {
                repository.saveAll(buffer);
                total += buffer.size();
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            repository.saveAll(buffer);
            total += buffer.size();
        }

        log.info("[PgSearch][SYNC][{}] count={} dedup_skipped={}", domain, total, skipped);
    }
}
