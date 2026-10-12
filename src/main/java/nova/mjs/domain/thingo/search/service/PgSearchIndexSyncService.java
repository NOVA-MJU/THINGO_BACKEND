package nova.mjs.domain.thingo.search.service;

import jakarta.persistence.EntityManager;
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
import nova.mjs.domain.thingo.search.academic.AcademicGuideCatalog;
import nova.mjs.domain.thingo.search.mapper.PgUnifiedSearchMapper;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
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

    private static final int BATCH_SIZE = 200;

    private final NoticeRepository noticeRepository;
    private final NewsRepository newsRepository;
    private final CommunityBoardRepository communityBoardRepository;
    private final DepartmentScheduleRepository departmentScheduleRepository;
    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    private final BroadcastRepository broadcastRepository;
    private final MjuCalendarRepository mjuCalendarRepository;
    private final AcademicGuideCatalog academicGuideCatalog;

    private final NoticeContentPreprocessor noticeContentPreprocessor;
    private final CommunityContentPreprocessor communityContentPreprocessor;

    private final UnifiedSearchIndexRepository repository;
    private final PgUnifiedSearchMapper mapper;
    private final EntityManager entityManager;

    /**
     * search_vector / title_vector 만 재생성한다(truncate/재토큰화 없음).
     *
     * 트리거(usi_update_search_vector) 누락으로 재색인된 행의 search_vector 가 NULL 이 되어
     * 키워드 검색이 전부 0 이 되는 사고의 경량 복구 경로. 기존 행의 search_tokens/title/content
     * 로부터 벡터만 다시 채우므로 수 초 내 끝나고(nginx 타임아웃 안 걸림), syncAll 의 무거운
     * collectAll(Komoran 재토큰화)/truncate 와 무관하게 동작한다.
     */
    @Transactional
    public void rebuildVectorsOnly() {
        log.info("[PgSearch][REBUILD-VECTORS] start");
        repository.rebuildVectors();
        log.info("[PgSearch][REBUILD-VECTORS] done");
    }

    /**
     * 전체 재구축(운영 전용). 인덱스를 비우고 소스 전체를 다시 적재한다.
     */
    @Transactional
    public void syncAll() {
        log.info("[PgSearch][SYNC] start");

        repository.truncate();

        Set<String> seenLinks = new HashSet<>();
        SyncStats stats = new SyncStats();

        insertSource("NOTICE", noticeRepository,
                e -> NoticeDocument.from(e, noticeContentPreprocessor), seenLinks, stats);
        insertSource("COMMUNITY", communityBoardRepository,
                e -> CommunityDocument.from(e, communityContentPreprocessor), seenLinks, stats);
        insertSource("NEWS", newsRepository, NewsDocument::from, seenLinks, stats);
        insertSource("DEPARTMENT_SCHEDULE", departmentScheduleRepository,
                DepartmentScheduleDocument::from, seenLinks, stats);
        insertSource("STUDENT_COUNCIL_NOTICE", studentCouncilNoticeRepository,
                StudentCouncilNoticeDocument::from, seenLinks, stats);
        insertSource("BROADCAST", broadcastRepository, BroadcastDocument::from, seenLinks, stats);
        insertSource("MJU_CALENDAR", mjuCalendarRepository, MjuCalendarDocument::from, seenLinks, stats);
        insertStaticAcademicGuides(seenLinks, stats);

        // 트리거 의존 제거: 삽입 후 search_vector/title_vector 를 직접 재생성한다(트리거 누락 안전망).
        repository.rebuildVectors();

        log.info("[PgSearch][SYNC] end. indexed={}, dedup_skipped={}, conversion_failed={}",
                stats.inserted, stats.dedupSkipped, stats.conversionFailed);
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

        Set<String> seenLinks = new HashSet<>();
        Set<String> desiredIds = new HashSet<>();
        ReconcileStats stats = new ReconcileStats();

        reconcileSource("NOTICE", noticeRepository,
                e -> NoticeDocument.from(e, noticeContentPreprocessor), seenLinks, desiredIds, stats);
        reconcileSource("COMMUNITY", communityBoardRepository,
                e -> CommunityDocument.from(e, communityContentPreprocessor), seenLinks, desiredIds, stats);
        reconcileSource("NEWS", newsRepository, NewsDocument::from, seenLinks, desiredIds, stats);
        reconcileSource("DEPARTMENT_SCHEDULE", departmentScheduleRepository,
                DepartmentScheduleDocument::from, seenLinks, desiredIds, stats);
        reconcileSource("STUDENT_COUNCIL_NOTICE", studentCouncilNoticeRepository,
                StudentCouncilNoticeDocument::from, seenLinks, desiredIds, stats);
        reconcileSource("BROADCAST", broadcastRepository,
                BroadcastDocument::from, seenLinks, desiredIds, stats);
        reconcileSource("MJU_CALENDAR", mjuCalendarRepository,
                MjuCalendarDocument::from, seenLinks, desiredIds, stats);
        reconcileStaticAcademicGuides(seenLinks, desiredIds, stats);

        deactivateMissing(desiredIds, stats);

        // 트리거 누락 안전망: 변경/삽입분은 트리거가 벡터를 갱신하므로, 벡터가 NULL로 남은 행만 채운다.
        repository.rebuildMissingVectors();

        log.info("[PgSearch][RECONCILE] end. new={}, updated={}, deactivated={}, unchanged={}",
                stats.inserted, stats.updated, stats.deactivated, stats.unchanged);
    }

    private <E, ID> void insertSource(String domain,
                                      JpaRepository<E, ID> sourceRepository,
                                      Function<E, ? extends SearchDocument> toDocument,
                                      Set<String> seenLinks,
                                      SyncStats totalStats) {
        int pageNumber = 0;
        SourceStats sourceStats = new SourceStats();
        boolean hasNext;

        do {
            Page<E> page = sourceRepository.findAll(PageRequest.of(
                    pageNumber, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            hasNext = page.hasNext();

            List<UnifiedSearchIndex> batch = convertPage(
                    domain, page.getContent(), toDocument, seenLinks, sourceStats);
            if (!batch.isEmpty()) {
                repository.saveAll(batch);
                repository.flush();
                entityManager.clear();
            }

            totalStats.inserted += batch.size();
            pageNumber++;
        } while (hasNext);

        totalStats.dedupSkipped += sourceStats.dedupSkipped;
        totalStats.conversionFailed += sourceStats.conversionFailed;
        logSourceStats(domain, sourceStats);
    }

    /** 게시판 전체를 건드리지 않고 배포 산출물의 학사안내 문서만 원자적으로 교체한다. */
    @Transactional
    public void syncAcademicGuides() {
        log.info("[PgSearch][ACADEMIC_SYNC] start");

        int deleted = repository.deleteAllByType("ACADEMIC_GUIDE");
        SyncStats stats = new SyncStats();
        insertStaticAcademicGuides(new HashSet<>(), stats);
        repository.rebuildVectorsByType("ACADEMIC_GUIDE");

        log.info(
                "[PgSearch][ACADEMIC_SYNC] end. deleted={}, indexed={}, dedup_skipped={}, conversion_failed={}",
                deleted, stats.inserted, stats.dedupSkipped, stats.conversionFailed);
    }

    private void insertStaticAcademicGuides(Set<String> seenLinks, SyncStats totalStats) {
        SourceStats sourceStats = new SourceStats();
        List<UnifiedSearchIndex> batch = convertPage(
                "ACADEMIC_GUIDE",
                academicGuideCatalog.documents(),
                Function.identity(),
                seenLinks,
                sourceStats);
        if (!batch.isEmpty()) {
            repository.saveAll(batch);
            repository.flush();
            entityManager.clear();
        }
        totalStats.inserted += batch.size();
        totalStats.dedupSkipped += sourceStats.dedupSkipped;
        totalStats.conversionFailed += sourceStats.conversionFailed;
        logSourceStats("ACADEMIC_GUIDE", sourceStats);
    }

    private <E, ID> void reconcileSource(String domain,
                                         JpaRepository<E, ID> sourceRepository,
                                         Function<E, ? extends SearchDocument> toDocument,
                                         Set<String> seenLinks,
                                         Set<String> desiredIds,
                                         ReconcileStats totalStats) {
        int pageNumber = 0;
        SourceStats sourceStats = new SourceStats();
        boolean hasNext;

        do {
            Page<E> page = sourceRepository.findAll(PageRequest.of(
                    pageNumber, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            hasNext = page.hasNext();

            List<UnifiedSearchIndex> desiredBatch = convertPage(
                    domain, page.getContent(), toDocument, seenLinks, sourceStats);
            if (!desiredBatch.isEmpty()) {
                reconcileBatch(desiredBatch, desiredIds, totalStats);
                repository.flush();
                entityManager.clear();
            }
            pageNumber++;
        } while (hasNext);

        logSourceStats(domain, sourceStats);
    }

    private void reconcileStaticAcademicGuides(Set<String> seenLinks,
                                                Set<String> desiredIds,
                                                ReconcileStats totalStats) {
        SourceStats sourceStats = new SourceStats();
        List<UnifiedSearchIndex> desiredBatch = convertPage(
                "ACADEMIC_GUIDE",
                academicGuideCatalog.documents(),
                Function.identity(),
                seenLinks,
                sourceStats);
        if (!desiredBatch.isEmpty()) {
            reconcileBatch(desiredBatch, desiredIds, totalStats);
            repository.flush();
            entityManager.clear();
        }
        logSourceStats("ACADEMIC_GUIDE", sourceStats);
    }

    private void reconcileBatch(List<UnifiedSearchIndex> desiredBatch,
                                Set<String> desiredIds,
                                ReconcileStats stats) {
        Map<String, UnifiedSearchIndex> existingById = new HashMap<>();
        List<String> batchIds = desiredBatch.stream()
                .map(UnifiedSearchIndex::getId)
                .toList();

        repository.findAllById(batchIds)
                .forEach(existing -> existingById.put(existing.getId(), existing));

        List<UnifiedSearchIndex> toSave = new ArrayList<>();
        for (UnifiedSearchIndex desired : desiredBatch) {
            desiredIds.add(desired.getId());
            UnifiedSearchIndex existing = existingById.get(desired.getId());
            if (existing == null) {
                toSave.add(desired);
                stats.inserted++;
            } else if (existing.differsFrom(desired)) {
                existing.updateFrom(desired);
                toSave.add(existing);
                stats.updated++;
            } else {
                stats.unchanged++;
            }
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
    }

    private void deactivateMissing(Set<String> desiredIds, ReconcileStats stats) {
        int pageNumber = 0;
        boolean hasNext;

        do {
            Page<UnifiedSearchIndex> page = repository.findAll(PageRequest.of(
                    pageNumber, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            hasNext = page.hasNext();

            for (UnifiedSearchIndex existing : page.getContent()) {
                if (!desiredIds.contains(existing.getId()) && Boolean.TRUE.equals(existing.getActive())) {
                    existing.deactivate();
                    stats.deactivated++;
                }
            }

            repository.flush();
            entityManager.clear();
            pageNumber++;
        } while (hasNext);
    }

    private <E> List<UnifiedSearchIndex> convertPage(String domain,
                                                      List<E> entities,
                                                      Function<E, ? extends SearchDocument> toDocument,
                                                      Set<String> seenLinks,
                                                      SourceStats stats) {
        List<UnifiedSearchIndex> out = new ArrayList<>(entities.size());
        for (E entity : entities) {
            SearchDocument doc;
            try {
                doc = toDocument.apply(entity);
            } catch (Exception e) {
                log.warn("[PgSearch][COLLECT][{}] doc convert failed", domain, e);
                stats.conversionFailed++;
                continue;
            }
            if (doc == null) {
                continue;
            }

            String link = doc.getLink();
            if (link != null && !link.isBlank() && !seenLinks.add(link)) {
                stats.dedupSkipped++;
                continue;
            }

            out.add(mapper.from(doc));
            stats.converted++;
        }
        return out;
    }

    private void logSourceStats(String domain, SourceStats stats) {
        log.info("[PgSearch][COLLECT][{}] added={} dedup_skipped={} conversion_failed={}",
                domain, stats.converted, stats.dedupSkipped, stats.conversionFailed);
    }

    private static final class SourceStats {
        private int converted;
        private int dedupSkipped;
        private int conversionFailed;
    }

    private static final class SyncStats {
        private int inserted;
        private int dedupSkipped;
        private int conversionFailed;
    }

    private static final class ReconcileStats {
        private int inserted;
        private int updated;
        private int deactivated;
        private int unchanged;
    }
}
