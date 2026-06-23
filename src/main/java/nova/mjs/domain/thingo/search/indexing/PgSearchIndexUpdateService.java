package nova.mjs.domain.thingo.search.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.ElasticSearch.indexing.update.SearchIndexUpdateService;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 좋아요/댓글 변경 시 PostgreSQL 통합 인덱스의 카운트를 부분 갱신한다.
 *
 * 게시글 본문 변경이 아니라 카운트만 바뀌는 경로(좋아요/댓글)는 엔티티 재저장 이벤트가
 * 발생하지 않으므로, 인덱스 행을 직접 찾아 카운트만 갱신한다.
 * (기존 ES 기반 구현을 PG 통합 인덱스 기준으로 대체)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgSearchIndexUpdateService implements SearchIndexUpdateService {

    // 통합 인덱스 id 규칙: {TYPE}:{ORIGINAL_ID}
    private static final String COMMUNITY_TYPE = "COMMUNITY";

    private final UnifiedSearchIndexRepository repository;

    @Override
    @Transactional
    public void updateCommunityCounts(UUID boardUuid, Integer likeCount, Integer commentCount) {
        String id = COMMUNITY_TYPE + ":" + boardUuid;
        repository.findById(id).ifPresentOrElse(
                doc -> doc.updateCounts(likeCount, commentCount),
                () -> log.debug("[PgSearch] 카운트 갱신 대상 없음 - id={}", id)
        );
    }
}
