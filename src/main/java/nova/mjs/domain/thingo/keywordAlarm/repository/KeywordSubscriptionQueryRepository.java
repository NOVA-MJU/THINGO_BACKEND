package nova.mjs.domain.thingo.keywordAlarm.repository;

import java.util.List;

/**
 * 키워드 구독 네이티브 매칭 (통합검색과 동일한 PostgreSQL FTS 사용).
 */
public interface KeywordSubscriptionQueryRepository {

    /**
     * 주어진 카테고리를 구독하면서, 키워드가 문서와 매칭되는 구독을 찾는다.
     *
     * @param category  AlarmCategory 이름 (NOTICE/MJU_CALENDAR/COMMUNITY)
     * @param docTokens KomoranTokenizerUtil.buildSearchTokens 로 만든 문서 토큰 문자열
     * @param docTitle  원문 제목. 형태소 분해로 쪼개진 복합어("수강신청" -> "수강 신청")를 보조로 잡는다.
     */
    List<KeywordMatch> findMatchingSubscriptions(String category, String docTokens, String docTitle);
}
