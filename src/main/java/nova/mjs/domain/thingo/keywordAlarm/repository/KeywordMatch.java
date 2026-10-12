package nova.mjs.domain.thingo.keywordAlarm.repository;

import java.util.UUID;

/**
 * 신규 콘텐츠와 매칭된 구독 한 건.
 * 소유자는 memberId(회원) 또는 deviceId(비로그인 기기) 중 하나만 채워진다.
 */
public record KeywordMatch(Long subscriptionId, Long memberId, UUID deviceId, String keyword, String topicId) {

    public KeywordMatch(Long subscriptionId, Long memberId, String keyword) {
        this(subscriptionId, memberId, null, keyword, null);
    }

    public KeywordMatch(Long subscriptionId, Long memberId, String keyword, String topicId) {
        this(subscriptionId, memberId, null, keyword, topicId);
    }
}
