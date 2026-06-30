package nova.mjs.domain.thingo.keywordAlarm.repository;

import java.util.List;

/**
 * 키워드 구독 네이티브 매칭 (통합검색과 동일한 PostgreSQL FTS 사용).
 */
public interface KeywordSubscriptionQueryRepository {

    /**
     * 주어진 카테고리를 구독하면서, 키워드가 문서 토큰과 매칭되는 구독을 찾는다.
     *
     * @param category  AlarmCategory 이름 (NOTICE/MJU_CALENDAR/COMMUNITY)
     * @param docTokens KomoranTokenizerUtil.buildSearchTokens 로 만든 문서 토큰 문자열
     */
    List<KeywordMatch> findMatchingSubscriptions(String category, String docTokens);
}
