package nova.mjs.domain.thingo.search.mapper;

import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 도메인 SearchDocument -> UnifiedSearchIndex 변환기.
 *
 * - id 규칙: {TYPE}:{ORIGINAL_ID}
 * - 토큰화는 기존 KomoranTokenizerUtil 재사용 (ES와 동일 사전/규칙).
 * - popularity 는 likeCount + commentCount + recency 기반 0~100 점.
 */
@Component
public class PgUnifiedSearchMapper {

    private static final double LIKE_WEIGHT = 1.0d;
    private static final double COMMENT_WEIGHT = 2.0d;
    private static final long RECENCY_HALFLIFE_DAYS = 30L;

    public String buildId(SearchDocument doc) {
        return buildId(doc.getType(), doc.getId());
    }

    public String buildId(String type, String originalId) {
        return safe(type) + ":" + safe(originalId);
    }

    public UnifiedSearchIndex from(SearchDocument doc) {
        String title = doc.getTitle();
        String content = doc.getContent();
        String category = doc.getCategory();

        String tokens = KomoranTokenizerUtil.buildSearchTokens(title, category, content);
        double popularity = computePopularity(doc.getLikeCount(), doc.getCommentCount(), doc.getInstant());

        return UnifiedSearchIndex.of(
                buildId(doc),
                doc.getId(),
                doc.getType(),
                category,
                title,
                content,
                doc.getAuthorName(),
                doc.getLink(),
                doc.getImageUrl(),
                doc.getLikeCount(),
                doc.getCommentCount(),
                popularity,
                doc.getInstant(),
                tokens
        );
    }

    /**
     * popularity = (likes*1 + comments*2) * recencyDecay
     *
     * - recencyDecay = 2^(-ageDays / halflife)
     * - halflife = 30일
     * - 미래/null 시점은 1.0 으로 처리
     */
    double computePopularity(Integer likeCount, Integer commentCount, Instant date) {
        double engagement = nz(likeCount) * LIKE_WEIGHT + nz(commentCount) * COMMENT_WEIGHT;
        if (engagement <= 0d) {
            return 0d;
        }
        double decay = recencyDecay(date);
        return engagement * decay;
    }

    private double recencyDecay(Instant date) {
        if (date == null) {
            return 1.0d;
        }
        long ageDays = Math.max(0L, Duration.between(date, Instant.now()).toDays());
        return Math.pow(0.5d, (double) ageDays / RECENCY_HALFLIFE_DAYS);
    }

    private double nz(Integer v) {
        return v == null ? 0d : v;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
