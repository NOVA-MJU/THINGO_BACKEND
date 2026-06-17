package nova.mjs.domain.thingo.search.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PostgreSQL FTS + pg_trgm 기반 통합 검색 native 구현.
 *
 * 매칭 전략:
 *  - keyword 비어있음: 필터 + 정렬만 적용
 *  - keyword 있음: Komoran 토큰화 OR-tsquery 매칭 OR search_tokens % keyword (trigram)
 *    (둘 다 GIN 인덱스 사용 -> seq scan 없음)
 *
 * 점수:
 *  - ts_rank(search_vector, to_tsquery) * 0.6
 *  + 제목 매칭 시 0.25 부스트
 *  + similarity(search_tokens, keyword) * 0.2
 *  + coalesce(popularity, 0) * 0.0001
 */
@Repository
public class UnifiedSearchIndexQueryRepositoryImpl implements UnifiedSearchIndexQueryRepository {

    private static final String ORDER_RELEVANCE = "relevance";
    private static final String ORDER_LATEST = "latest";
    private static final String ORDER_OLDEST = "oldest";

    private static final double SUGGEST_TRIGRAM_THRESHOLD = 0.2d;

    /*
     * 카테고리 가중치 (NOTICE 도메인 한정).
     * - 학생 검색 빈도 기준 순위: general > academic > scholarship > career > activity > rule
     * - keyword 매칭 점수(ts_rank*0.6 + similarity*0.4, 보통 0~1)가 dominant 하도록 약 boost(<=0.10)로 유지.
     */
    private static final String CATEGORY_WEIGHT_EXPR =
            " CASE category "
                    + "  WHEN 'general' THEN 0.10 "
                    + "  WHEN 'academic' THEN 0.08 "
                    + "  WHEN 'scholarship' THEN 0.06 "
                    + "  WHEN 'career' THEN 0.04 "
                    + "  WHEN 'activity' THEN 0.02 "
                    + "  WHEN 'rule' THEN 0.00 "
                    + "  ELSE 0.00 "
                    + " END ";

    /*
     * 도메인 타입 가중치.
     * - 학사 공지/학사일정 우선, 외부 콘텐츠(NEWS/BROADCAST) 하위.
     * - 카테고리 가중치와 동일한 약 boost 스케일(<=0.10).
     */
    private static final String TYPE_WEIGHT_EXPR =
            " CASE type "
                    + "  WHEN 'NOTICE' THEN 0.10 "
                    + "  WHEN 'MJU_CALENDAR' THEN 0.08 "
                    + "  WHEN 'DEPARTMENT_SCHEDULE' THEN 0.06 "
                    + "  WHEN 'STUDENT_COUNCIL_NOTICE' THEN 0.05 "
                    + "  WHEN 'COMMUNITY' THEN 0.04 "
                    + "  WHEN 'NEWS' THEN 0.02 "
                    + "  WHEN 'BROADCAST' THEN 0.01 "
                    + "  ELSE 0.00 "
                    + " END ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<SearchResultRow> search(String keyword,
                                        String type,
                                        String category,
                                        String order,
                                        String hotPattern,
                                        double hotBoost,
                                        Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasHot = hotPattern != null && !hotPattern.isBlank() && hotBoost > 0d;
        String resolvedOrder = resolveOrder(order, hasKeyword);

        // 검색어를 색인과 동일한 Komoran 기준으로 토큰화해 OR(`|`) tsquery 로 만든다.
        // 복합어 분해 + 다어절 OR 매칭으로 recall 확보, 정밀도는 ts_rank + 제목 부스트로 보정.
        String tsQuery = hasKeyword ? KomoranTokenizerUtil.buildTsQuery(keyword) : "";
        boolean hasTsQuery = !tsQuery.isBlank();

        // 모든 토큰을 포함하는 문서에 가산점을 주기 위한 AND 쿼리(coverage boost).
        String tsQueryAnd = hasKeyword ? KomoranTokenizerUtil.buildTsQueryAnd(keyword) : "";
        boolean hasTsQueryAnd = !tsQueryAnd.isBlank();

        // 키워드가 있으나 의미 토큰이 전혀 없으면(자모/기호 노이즈) 매칭 대상이 없다.
        // DB 조회 없이 빈 결과를 즉시 반환한다(노이즈 입력이 느린 trigram 스캔을 타지 않도록).
        if (hasKeyword && !hasTsQuery) {
            return new PageImpl<>(java.util.List.of(), pageable, 0L);
        }

        StringBuilder where = new StringBuilder(" WHERE active = TRUE ");
        if (type != null && !type.isBlank()) {
            where.append(" AND type = :type ");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = :category ");
        }
        if (hasKeyword) {
            // 토큰화된 검색어로 tsvector FTS 매칭 (idx_usi_search_vector GIN, seq scan 없음).
            where.append(" AND search_vector @@ to_tsquery('simple', :tsQuery) ");
        }

        String hotBoostExpr = hasHot
                ? " + CASE WHEN coalesce(title,'') ~* :hotPattern THEN :hotBoost ELSE 0 END "
                : " ";

        // 카테고리/타입 가중치는 keyword 유무와 무관하게 항상 합산한다.
        // - keyword 있을 때: ts_rank/제목부스트가 dominant, 가중치는 tie-breaker 수준.
        // - keyword 없을 때: 가중치 + popularity 가 정렬 기준.
        String weightExpr = " + " + CATEGORY_WEIGHT_EXPR + " + " + TYPE_WEIGHT_EXPR + " ";

        // FTS 점수: ts_rank + 제목 매칭 부스트(본문에만 스친 문서가 제목 정매칭을 누르지 않도록).
        // 제목 부스트는 WHERE 통과 행에만 계산되므로(인덱스 필터 후) 성능 부담 없음.
        // 점수는 ts_rank + 제목 매칭 부스트만 사용한다.
        // trigram similarity(search_tokens, keyword) 는 KB 급 토큰 blob 을 매칭된 전 행에 대해
        // 재계산하므로 지연의 주원인이었다. trigram 은 WHERE(% 연산자, GIN 인덱스)에서 매칭에만 쓰고
        // 랭킹에서는 제거한다.
        // 제목이 모든 토큰을 포함하면 강한 가산점(본문에만 co-occur 하는 문서보다 우선).
        String coverageBoost = hasTsQueryAnd
                ? " + CASE WHEN to_tsvector('simple', coalesce(title,'')) @@ to_tsquery('simple', :tsQueryAnd) "
                + "        THEN 0.4 ELSE 0 END "
                : " ";
        String ftsScore = hasTsQuery
                ? " ts_rank(search_vector, to_tsquery('simple', :tsQuery)) * 0.6 "
                + " + CASE WHEN to_tsvector('simple', coalesce(title,'')) @@ to_tsquery('simple', :tsQuery) "
                + "        THEN 0.25 ELSE 0 END "
                + coverageBoost
                : " 0.0 ";

        String scoreExpr = hasKeyword
                ? " ( " + ftsScore
                + "  + coalesce(popularity, 0) * 0.0001 "
                + weightExpr
                + hotBoostExpr
                + " ) "
                : (hasHot
                ? " (coalesce(popularity, 0) * 0.0001 " + weightExpr + hotBoostExpr + ") "
                : " (0.0 " + weightExpr + ") ");

        String headlineTitle = hasTsQuery
                ? " ts_headline('simple', coalesce(title,''), to_tsquery('simple', :tsQuery), "
                + " 'StartSel=<em>,StopSel=</em>,MaxFragments=1,MaxWords=20,MinWords=1') "
                : " title ";

        String headlineContent = hasTsQuery
                ? " ts_headline('simple', coalesce(content,''), to_tsquery('simple', :tsQuery), "
                + " 'StartSel=<em>,StopSel=</em>,MaxFragments=1,MaxWords=30,MinWords=1') "
                : " content ";

        String orderBy = switch (resolvedOrder) {
            case ORDER_LATEST -> " ORDER BY date DESC NULLS LAST ";
            case ORDER_OLDEST -> " ORDER BY date ASC NULLS LAST ";
            default -> " ORDER BY score DESC, date DESC NULLS LAST ";
        };

        String selectSql =
                "SELECT id, original_id, type, category, title, "
                        + headlineTitle + " AS highlighted_title, "
                        + " content, "
                        + headlineContent + " AS highlighted_content, "
                        + " author_name, link, image_url, like_count, comment_count, date, "
                        + scoreExpr + " AS score "
                        + " FROM unified_search_index "
                        + where
                        + orderBy
                        + " LIMIT :limit OFFSET :offset ";

        String countSql = "SELECT count(*) FROM unified_search_index " + where;

        Query selectQuery = em.createNativeQuery(selectSql);
        Query countQuery = em.createNativeQuery(countSql);

        bindParams(selectQuery, tsQuery, hasTsQuery, type, category);
        bindParams(countQuery, tsQuery, hasTsQuery, type, category);

        // :tsQueryAnd 는 SELECT 점수식에만 존재한다(WHERE/count 에는 없음) -> selectQuery 에만 바인딩.
        if (hasTsQueryAnd) {
            selectQuery.setParameter("tsQueryAnd", tsQueryAnd);
        }

        if (hasHot) {
            selectQuery.setParameter("hotPattern", hotPattern);
            selectQuery.setParameter("hotBoost", hotBoost);
        }

        selectQuery.setParameter("limit", pageable.getPageSize());
        selectQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = selectQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<SearchResultRow> content = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            content.add(toRow(r));
        }

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<String> suggest(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String sql =
                "SELECT title FROM unified_search_index "
                        + " WHERE active = TRUE "
                        + "   AND ( title ILIKE :prefix "
                        + "         OR word_similarity(:keyword, title) > :trgmThreshold ) "
                        + " ORDER BY word_similarity(:keyword, title) DESC, date DESC NULLS LAST "
                        + " LIMIT :limit ";

        Query q = em.createNativeQuery(sql);
        q.setParameter("prefix", keyword + "%");
        q.setParameter("keyword", keyword);
        q.setParameter("trgmThreshold", SUGGEST_TRIGRAM_THRESHOLD);
        q.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Object> result = q.getResultList();
        return result.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .toList();
    }

    private void bindParams(Query q, String tsQuery, boolean hasTsQuery, String type, String category) {
        if (type != null && !type.isBlank()) {
            q.setParameter("type", type);
        }
        if (category != null && !category.isBlank()) {
            q.setParameter("category", category);
        }
        if (hasTsQuery) {
            q.setParameter("tsQuery", tsQuery);
        }
    }

    private String resolveOrder(String order, boolean hasKeyword) {
        if (order == null || order.isBlank()) {
            return hasKeyword ? ORDER_RELEVANCE : ORDER_LATEST;
        }
        String lower = order.toLowerCase();
        if (ORDER_LATEST.equals(lower) || ORDER_OLDEST.equals(lower) || ORDER_RELEVANCE.equals(lower)) {
            return lower;
        }
        return hasKeyword ? ORDER_RELEVANCE : ORDER_LATEST;
    }

    private SearchResultRow toRow(Object[] r) {
        return new SearchResultRow(
                asString(r[0]),
                asString(r[1]),
                asString(r[2]),
                asString(r[3]),
                asString(r[4]),
                asString(r[5]),
                asString(r[6]),
                asString(r[7]),
                asString(r[8]),
                asString(r[9]),
                asString(r[10]),
                asInt(r[11]),
                asInt(r[12]),
                asInstant(r[13]),
                asDouble(r[14])
        );
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

    private Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.valueOf(v.toString());
    }

    private Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof BigDecimal b) return b.doubleValue();
        return Double.valueOf(v.toString());
    }

    private Instant asInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Timestamp t) return t.toInstant();
        if (v instanceof java.sql.Date d) return d.toInstant();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return null;
    }
}
