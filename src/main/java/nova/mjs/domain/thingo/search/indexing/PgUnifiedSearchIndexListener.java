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
    }

    private void delete(SearchDocument doc) {
        String id = mapper.buildId(doc);
        repository.deleteById(id);
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }
}
