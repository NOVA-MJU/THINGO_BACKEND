package nova.mjs.domain.thingo.realtimeKeyword;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.ElasticSearch.Repository.PostgresUnifiedSearchRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RealtimeKeywordService {

    private final PostgresUnifiedSearchRepository postgresUnifiedSearchRepository;

    @Qualifier("keywordRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;

    private static final String ZSET_KEY = "realtime_keywords";
    private static final String LIST_KEY_PREFIX = "search:history";

    private static final long ttl = 3L * 24 * 60 * 60 * 1000;

    public void recordSearch(String keyword) {
        if (keyword == null || keyword.trim().isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();

        redisTemplate.opsForZSet().incrementScore(ZSET_KEY, keyword, 1.0);
        redisTemplate.opsForList().rightPush(LIST_KEY_PREFIX + keyword, String.valueOf(now));

        postgresUnifiedSearchRepository.insertSearchLog(keyword, null);
    }

    public List<String> getTopKeywords(int topN) {
        List<String> topKeywords = postgresUnifiedSearchRepository.topKeywords(topN);
        if (!topKeywords.isEmpty()) {
            return topKeywords;
        }

        Set<String> keywords = redisTemplate.opsForZSet().reverseRange(ZSET_KEY, 0, topN - 1);
        return keywords != null ? new ArrayList<>(keywords) : Collections.emptyList();
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void expiredSearchRecords() {
        long now = System.currentTimeMillis();

        Set<String> keywords = redisTemplate.opsForZSet().range(ZSET_KEY, 0, -1);

        if (keywords == null || keywords.isEmpty()) return;

        for (String keyword : keywords) {
            String historyKey = LIST_KEY_PREFIX + keyword;

            List<String> timestamps = redisTemplate.opsForList().range(historyKey, 0, -1);

            if (timestamps == null || timestamps.isEmpty()) continue;

            for (String ts : new ArrayList<>(timestamps)) {
                long time = Long.parseLong(ts);
                if (now - time > ttl) {
                    redisTemplate.opsForList().remove(historyKey, 1, ts);
                    redisTemplate.opsForZSet().incrementScore(ZSET_KEY, keyword, -1.0);
                }
            }

            Double score = redisTemplate.opsForZSet().score(ZSET_KEY, keyword);
            if (score != null && score <= 0) {
                redisTemplate.opsForZSet().remove(ZSET_KEY, keyword);
                redisTemplate.delete(historyKey);
            }
        }
    }
}
