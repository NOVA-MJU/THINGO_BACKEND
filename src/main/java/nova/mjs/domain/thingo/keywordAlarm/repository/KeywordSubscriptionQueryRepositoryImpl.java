package nova.mjs.domain.thingo.keywordAlarm.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 키워드 구독 네이티브 매칭 구현.
 *
 * 통합검색과 동일하게 PostgreSQL FTS(simple config)를 사용한다.
 * 단, 영속된 인덱스 row(검색 리스너가 비동기로 적재)에 의존하지 않고,
 * 이벤트가 전달한 문서 토큰(docTokens)을 즉석에서 to_tsvector 로 만들어 매칭한다.
 * -> 검색 인덱싱 리스너와의 AFTER_COMMIT 실행 순서와 무관하게 동작(누락 방지).
 *
 * keyword 는 접두(prefix) tsquery 로 매칭한다.
 * 의미: "키워드로 시작하는 단어가 문서 토큰에 등장하는가" (예: '장학' -> '장학금'/'장학생'도 매칭).
 * 사용자 입력은 영숫자/한글만 남겨(regexp_replace) tsquery 연산자 주입을 차단하고, 빈 키워드는 제외한다.
 */
@Repository
public class KeywordSubscriptionQueryRepositoryImpl implements KeywordSubscriptionQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** ks.keyword 를 안전한 lexeme 으로 정규화(영숫자/한글만, 소문자) */
    private static final String SANITIZED_KEYWORD =
            "regexp_replace(lower(ks.keyword), '[^a-z0-9가-힣]', '', 'g')";

    private static final String MATCH_SQL = """
            SELECT ks.keyword_subscription_id, ks.member_id, ks.keyword
            FROM keyword_subscription ks
            JOIN keyword_subscription_category c
                 ON c.keyword_subscription_id = ks.keyword_subscription_id
            WHERE c.category = :category
              AND %1$s <> ''
              AND to_tsvector('simple', cast(:docTokens AS text))
                  @@ to_tsquery('simple', %1$s || ':*')
            """.formatted(SANITIZED_KEYWORD);

    @Override
    @SuppressWarnings("unchecked")
    public List<KeywordMatch> findMatchingSubscriptions(String category, String docTokens) {
        if (docTokens == null || docTokens.isBlank()) {
            return List.of();
        }

        Query query = entityManager.createNativeQuery(MATCH_SQL)
                .setParameter("category", category)
                .setParameter("docTokens", docTokens);

        List<Object[]> rows = query.getResultList();
        List<KeywordMatch> matches = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Long subscriptionId = ((Number) row[0]).longValue();
            Long memberId = ((Number) row[1]).longValue();
            String keyword = (String) row[2];
            matches.add(new KeywordMatch(subscriptionId, memberId, keyword));
        }
        return matches;
    }
}
