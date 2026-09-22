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
 *
 * 보조 조건으로 원문 제목의 부분일치도 본다. 형태소 분석기가 복합어를 쪼개면
 * ('수강신청' -> '수강' + '신청') 접두 tsquery 가 원래 키워드를 놓치기 때문이다.
 * 제목도 키워드와 같은 방식으로 영숫자/한글만 남겨 비교하므로 공백·괄호 차이에 영향받지 않고,
 * LIKE 메타문자(%%, _)도 정규화 단계에서 제거돼 패턴 주입이 불가능하다.
 *
 * 단 3글자 이상 키워드에만 적용한다. 2글자는 형태소 토큰과 길이가 같아 접두 매칭으로 이미 잡히고,
 * 부분일치로 열면 '공지'가 '인공지능'에 걸리는 식의 오탐이 생긴다(운영 데이터로 확인).
 *
 * 마지막 조건은 복합어 사이에 말이 끼는 경우를 잡는다.
 * '해외탐방' 구독이 '해외문화탐방' 공지를 놓치던 문제 -> 키워드를 2음절씩 끊어
 * '해외[가-힣]{0,3}탐방' 으로 찾는다. 첫 조각은 반드시 단어 경계에서 시작해야 한다.
 * 경계 조건이 없으면 '수강신청' 이 '재수강 ... 신청원서' 에 걸린다(운영 데이터로 확인).
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
              AND ks.enabled = true
              AND ks.topic_id IS NULL
              AND %1$s <> ''
              AND (
                    to_tsvector('simple', cast(:docTokens AS text))
                        @@ to_tsquery('simple', %1$s || ':*')
                 OR (
                        char_length(%1$s) >= 3
                    AND regexp_replace(lower(cast(:docTitle AS text)), '[^a-z0-9가-힣]', '', 'g')
                            LIKE '%%' || %1$s || '%%'
                 )
                 OR (
                        char_length(%1$s) >= 4
                    AND %1$s ~ '^[가-힣]+$'
                    AND lower(cast(:docTitle AS text)) ~ ('(^|[^가-힣])' ||
                            regexp_replace(%1$s, '(..)(?=.)', '\\1[가-힣]{0,3}', 'g'))
                 )
              )
            """.formatted(SANITIZED_KEYWORD);

    @Override
    @SuppressWarnings("unchecked")
    public List<KeywordMatch> findMatchingSubscriptions(String category, String docTokens, String docTitle) {
        if (docTokens == null || docTokens.isBlank()) {
            return List.of();
        }

        Query query = entityManager.createNativeQuery(MATCH_SQL)
                .setParameter("category", category)
                .setParameter("docTokens", docTokens)
                .setParameter("docTitle", docTitle == null ? "" : docTitle);

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
