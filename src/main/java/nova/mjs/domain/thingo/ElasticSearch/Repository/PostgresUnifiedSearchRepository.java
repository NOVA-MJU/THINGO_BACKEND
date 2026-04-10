package nova.mjs.domain.thingo.ElasticSearch.Repository;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.ElasticSearch.SearchResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PostgresUnifiedSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<SearchResponseDTO> search(String keyword,
                                          String type,
                                          String category,
                                          String order,
                                          Pageable pageable) {

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String tsQuery = normalizedKeyword.isBlank()
                ? ""
                : normalizedKeyword.replaceAll("\\s+", " & ");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", normalizedKeyword)
                .addValue("tsQuery", tsQuery)
                .addValue("type", type)
                .addValue("category", category)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String where = """
                WHERE active = true
                  AND (:type IS NULL OR type = :type)
                  AND (:category IS NULL OR category = :category)
                """;

        String rankExpr = """
                CASE
                    WHEN :tsQuery = '' THEN 0.0
                    ELSE ts_rank_cd(
                            weighted_tsv,
                            websearch_to_tsquery('simple', :tsQuery),
                            4
                         )
                END
                """;

        String searchConstraint = """
                AND (
                    :keyword = ''
                    OR weighted_tsv @@ websearch_to_tsquery('simple', :tsQuery)
                    OR title ILIKE CONCAT('%', :keyword, '%')
                    OR content ILIKE CONCAT('%', :keyword, '%')
                )
                """;

        String orderBy = switch (normalizeOrder(order)) {
            case "latest" -> " ORDER BY created_at DESC, id DESC ";
            case "oldest" -> " ORDER BY created_at ASC, id ASC ";
            default -> " ORDER BY rank_score DESC, created_at DESC, id DESC ";
        };

        String sql = """
                SELECT id,
                       original_id,
                       type,
                       title,
                       content,
                       created_at,
                       link,
                       category,
                       image_url,
                       author_name,
                       like_count,
                       comment_count,
                       %s AS rank_score
                FROM thingo_search_document
                %s
                %s
                """.formatted(rankExpr, where, searchConstraint)
                + orderBy
                + " LIMIT :limit OFFSET :offset";

        List<SearchResponseDTO> content = jdbcTemplate.query(sql, params, searchRowMapper());

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM thingo_search_document " + where + searchConstraint,
                params,
                Long.class
        );

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<String> autocomplete(String keyword, Long userId, int size) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", normalized)
                .addValue("size", size)
                .addValue("userId", userId);

        String sql = """
                WITH personal AS (
                    SELECT keyword,
                           COUNT(*)::double precision AS personal_weight,
                           MAX(searched_at) AS last_searched_at
                    FROM search_query_log
                    WHERE (:userId IS NULL OR user_id = :userId)
                      AND keyword ILIKE CONCAT(:keyword, '%')
                    GROUP BY keyword
                ),
                global_pop AS (
                    SELECT keyword,
                           COUNT(*)::double precision AS global_popularity,
                           MAX(searched_at) AS last_searched_at
                    FROM search_query_log
                    WHERE keyword ILIKE CONCAT(:keyword, '%')
                    GROUP BY keyword
                ),
                title_pop AS (
                    SELECT title AS keyword,
                           COUNT(*)::double precision AS title_hits
                    FROM thingo_search_document
                    WHERE title ILIKE CONCAT(:keyword, '%')
                    GROUP BY title
                )
                SELECT candidate.keyword
                FROM (
                    SELECT COALESCE(p.keyword, g.keyword, t.keyword) AS keyword,
                           COALESCE(p.personal_weight, 0) AS personal_weight,
                           COALESCE(g.global_popularity, 0) AS global_popularity,
                           COALESCE(t.title_hits, 0) AS title_hits,
                           GREATEST(COALESCE(p.last_searched_at, '-infinity'::timestamp),
                                    COALESCE(g.last_searched_at, '-infinity'::timestamp)) AS searched_at,
                           (
                             (COALESCE(p.personal_weight, 0) * 0.45)
                             + (COALESCE(g.global_popularity, 0) * 0.35)
                             + (COALESCE(t.title_hits, 0) * 0.20)
                           )
                           * EXP(
                                -0.000015
                                * EXTRACT(EPOCH FROM (NOW() - GREATEST(COALESCE(p.last_searched_at, NOW()), COALESCE(g.last_searched_at, NOW()))))
                           ) AS score
                    FROM personal p
                    FULL OUTER JOIN global_pop g ON p.keyword = g.keyword
                    FULL OUTER JOIN title_pop t ON COALESCE(p.keyword, g.keyword) = t.keyword
                ) candidate
                WHERE candidate.keyword IS NOT NULL
                ORDER BY candidate.score DESC, candidate.keyword ASC
                LIMIT :size
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("keyword"));
    }

    public List<String> topKeywords(int topN) {
        String sql = """
                SELECT keyword
                FROM search_query_log
                WHERE searched_at >= NOW() - INTERVAL '3 days'
                GROUP BY keyword
                ORDER BY COUNT(*) DESC, MAX(searched_at) DESC
                LIMIT :topN
                """;

        return jdbcTemplate.query(sql, Map.of("topN", topN), (rs, rowNum) -> rs.getString("keyword"));
    }

    public void insertSearchLog(String keyword, Long userId) {
        if (keyword == null || keyword.trim().isBlank()) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO search_query_log(keyword, user_id, searched_at)
                VALUES (:keyword, :userId, NOW())
                """,
                new MapSqlParameterSource()
                        .addValue("keyword", keyword.trim())
                        .addValue("userId", userId)
        );
    }

    public void rebuildSearchDocuments(List<SearchWriteModel> rows) {
        jdbcTemplate.update("TRUNCATE TABLE thingo_search_document", new MapSqlParameterSource());

        String sql = """
                INSERT INTO thingo_search_document(
                    id, original_id, type, title, title_normalized, content, content_normalized,
                    category, category_normalized, search_tokens, link, image_url, created_at,
                    updated_at, active, popularity, like_count, comment_count, author_name
                )
                VALUES (
                    :id, :originalId, :type, :title, :titleNormalized, :content, :contentNormalized,
                    :category, :categoryNormalized, :searchTokens, :link, :imageUrl, :createdAt,
                    :updatedAt, :active, :popularity, :likeCount, :commentCount, :authorName
                )
                """;

        SqlParameterSourceBuilder.batchUpdate(jdbcTemplate, sql, rows);
    }

    public void refreshSearchVectors() {
        jdbcTemplate.update("""
                UPDATE thingo_search_document
                SET weighted_tsv =
                      setweight(to_tsvector('simple', COALESCE(title, '')), 'A')
                   || setweight(to_tsvector('simple', COALESCE(category, '')), 'B')
                   || setweight(to_tsvector('simple', COALESCE(search_tokens, '')), 'A')
                   || setweight(to_tsvector('simple', COALESCE(content, '')), 'D'),
                    updated_at = NOW()
                """, new MapSqlParameterSource());
    }

    private RowMapper<SearchResponseDTO> searchRowMapper() {
        return (rs, rowNum) -> SearchResponseDTO.builder()
                .id(rs.getString("id"))
                .highlightedTitle(rs.getString("title"))
                .highlightedContent(rs.getString("content"))
                .date(toInstant(rs.getTimestamp("created_at")))
                .link(rs.getString("link"))
                .category(rs.getString("category"))
                .type(rs.getString("type") == null ? null : rs.getString("type").toLowerCase())
                .imageUrl(rs.getString("image_url"))
                .score(rs.getFloat("rank_score"))
                .authorName(rs.getString("author_name"))
                .likeCount(rs.getObject("like_count", Integer.class))
                .commentCount(rs.getObject("comment_count", Integer.class))
                .build();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeOrder(String order) {
        if ("latest".equalsIgnoreCase(order)) {
            return "latest";
        }
        if ("oldest".equalsIgnoreCase(order)) {
            return "oldest";
        }
        return "relevance";
    }

    public record SearchWriteModel(
            String id,
            String originalId,
            String type,
            String title,
            String titleNormalized,
            String content,
            String contentNormalized,
            String category,
            String categoryNormalized,
            String searchTokens,
            String link,
            String imageUrl,
            Instant createdAt,
            Instant updatedAt,
            boolean active,
            Double popularity,
            Integer likeCount,
            Integer commentCount,
            String authorName
    ) {
    }

    private static final class SqlParameterSourceBuilder {
        private SqlParameterSourceBuilder() {
        }

        static void batchUpdate(NamedParameterJdbcTemplate jdbcTemplate,
                                String sql,
                                List<SearchWriteModel> rows) {
            var sources = rows.stream()
                    .map(row -> new MapSqlParameterSource()
                            .addValue("id", row.id())
                            .addValue("originalId", row.originalId())
                            .addValue("type", row.type())
                            .addValue("title", row.title())
                            .addValue("titleNormalized", row.titleNormalized())
                            .addValue("content", row.content())
                            .addValue("contentNormalized", row.contentNormalized())
                            .addValue("category", row.category())
                            .addValue("categoryNormalized", row.categoryNormalized())
                            .addValue("searchTokens", row.searchTokens())
                            .addValue("link", row.link())
                            .addValue("imageUrl", row.imageUrl())
                            .addValue("createdAt", row.createdAt() == null ? null : Timestamp.from(row.createdAt()))
                            .addValue("updatedAt", row.updatedAt() == null ? null : Timestamp.from(row.updatedAt()))
                            .addValue("active", row.active())
                            .addValue("popularity", row.popularity())
                            .addValue("likeCount", row.likeCount())
                            .addValue("commentCount", row.commentCount())
                            .addValue("authorName", row.authorName()))
                    .toArray(MapSqlParameterSource[]::new);

            jdbcTemplate.batchUpdate(sql, sources);
        }
    }
}
