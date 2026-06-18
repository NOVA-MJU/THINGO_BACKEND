package nova.mjs.domain.thingo.search.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.ElasticSearch.indexing.event.EntityIndexEvent;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.mapper.PgUnifiedSearchMapper;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;

/**
 * EntityIndexEvent 를 PostgreSQL 통합 인덱스에 반영한다.
 *
 * - 기존 ES 리스너(SearchIndexEventListener)와 병행 동작한다.
 * - DB 커밋 이후에만 반영 (유령 문서 방지).
 * - 새로운 트랜잭션으로 처리해 원본 트랜잭션 상태와 분리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgUnifiedSearchIndexListener {

    private final UnifiedSearchIndexRepository repository;
    private final PgUnifiedSearchMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EntityIndexEvent<? extends SearchDocument> event) {
        if (event == null || event.getDocument() == null || event.getAction() == null) {
            log.warn("[PgSearch] invalid event. event={}", event);
            return;
        }

        SearchDocument doc = event.getDocument();
        String id = safe(doc.getId());
        String type = safe(doc.getType());

        try {
            switch (event.getAction()) {
                case INSERT, UPDATE -> upsert(doc);
                case DELETE -> delete(doc);
                default -> log.warn("[PgSearch] unsupported action. type={}, action={}, id={}",
                        type, event.getAction(), id);
            }
            log.info("[PgSearch] [{}] {} ok (id={})", type, event.getAction(), id);
        } catch (Exception e) {
            log.error("[PgSearch] [{}] {} failed (id={})", type, event.getAction(), id, e);
        }
    }

    private void upsert(SearchDocument doc) {
        UnifiedSearchIndex incoming = mapper.from(doc);
        repository.findById(incoming.getId())
                .ifPresentOrElse(
                        existing -> existing.updateFrom(incoming),
                        () -> repository.save(incoming)
                );
        // 같은 원문(link)이 여러 카테고리 게시판에 동시 게재되면 서로 다른 id 로 인덱싱되어
        // 검색 결과에 중복 노출된다. 실시간 경로에서도 link 기준으로 1건만 active 로 collapse 한다.
        collapseByLink(incoming.getLink());
    }

    /**
     * 동일 link 중복을 active 1건으로 정리한다.
     *
     * canonical(대표) 선택 규칙:
     *  - 이미 active 인 행이 있으면 그 중 최소 id 를 유지한다(reconcile/이전 선택과 수렴 → 카테고리 깜빡임 방지).
     *  - 전부 비활성이면 최소 id 를 살린다.
     * 나머지 active 행은 deactivate 한다(물리 삭제 아님 → 검색 active=TRUE 필터로 자연히 제외).
     *
     * link 가 null/blank 면 식별 불가하므로 collapse 대상에서 제외한다.
     */
    public void collapseByLink(String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        List<UnifiedSearchIndex> siblings = repository.findByLink(link);
        if (siblings.size() <= 1) {
            return;
        }
        UnifiedSearchIndex canonical = siblings.stream()
                .filter(s -> Boolean.TRUE.equals(s.getActive()))
                .min(Comparator.comparing(UnifiedSearchIndex::getId))
                .orElseGet(() -> siblings.stream()
                        .min(Comparator.comparing(UnifiedSearchIndex::getId))
                        .orElseThrow());

        for (UnifiedSearchIndex sibling : siblings) {
            if (sibling.getId().equals(canonical.getId())) {
                sibling.activate();
            } else if (Boolean.TRUE.equals(sibling.getActive())) {
                sibling.deactivate();
            }
        }
        log.info("[PgSearch] link collapse. link={}, kept={}, total={}",
                link, canonical.getId(), siblings.size());
    }

    private void delete(SearchDocument doc) {
        String id = mapper.buildId(doc);
        repository.deleteById(id);
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
