package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.extern.slf4j.Slf4j;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordMatch;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 키워드 매칭 파이프라인.
 *
 * 신규 콘텐츠(SearchDocument)를 구독 키워드와 매칭해 알림 내역을 적재하고,
 * 발송 대상(기기 토큰 + 페이로드) 목록을 반환한다. 실제 FCM 발송은 호출부(리스너)가
 * 이 트랜잭션이 끝난 뒤 비동기로 수행한다(외부 호출을 트랜잭션 밖으로).
 */
@Slf4j
@Service
public class KeywordMatchingService {

    /** 같은 (구독, 콘텐츠) 재알림 차단 캐시 TTL */
    private static final Duration DEDUP_TTL = Duration.ofDays(7);
    private static final String DEDUP_KEY_PREFIX = "kwalarm:dedup:";

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;
    private final RedisTemplate<String, String> keywordRedisTemplate;

    public KeywordMatchingService(KeywordSubscriptionRepository keywordSubscriptionRepository,
                                  NotificationHistoryRepository notificationHistoryRepository,
                                  DeviceTokenRepository deviceTokenRepository,
                                  MemberRepository memberRepository,
                                  @Qualifier("keywordRedisTemplate") RedisTemplate<String, String> keywordRedisTemplate) {
        this.keywordSubscriptionRepository = keywordSubscriptionRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.memberRepository = memberRepository;
        this.keywordRedisTemplate = keywordRedisTemplate;
    }

    /**
     * 문서를 구독과 매칭해 알림 내역을 저장하고 발송 대상을 모은다.
     *
     * 한 콘텐츠가 한 회원의 여러 키워드에 걸려도 알림은 1건(회원 단위로 묶음, 대표 키워드 사용).
     *
     * @return 회원별 FCM 발송 단위 목록 (기기 토큰이 없는 회원은 제외)
     */
    @Transactional
    public List<FcmDispatch> matchAndCollect(SearchDocument doc) {
        // 1. MVP 카테고리(NOTICE/MJU_CALENDAR/COMMUNITY) 외 콘텐츠는 알림 대상 아님
        AlarmCategory category = AlarmCategory.fromSearchType(doc.getType()).orElse(null);
        if (category == null) {
            return List.of();
        }

        // 2. 제목만 토큰화(정확도 우선). 본문은 매칭에서 제외 -> "그 글의 주제"일 때만 알림.
        String docTokens = KomoranTokenizerUtil.buildSearchTokens(doc.getTitle());
        if (docTokens == null || docTokens.isBlank()) {
            return List.of();
        }

        // 3. 카테고리 + 키워드(접두 FTS) 매칭 구독 조회
        long startedAt = System.currentTimeMillis();
        String searchIndexId = buildSearchIndexId(doc.getType(), doc.getId());
        List<KeywordMatch> matches =
                keywordSubscriptionRepository.findMatchingSubscriptions(category.name(), docTokens);
        long elapsed = System.currentTimeMillis() - startedAt;
        // 관측 장치: 구독 폭증 시 매칭이 느려지는지 감시(임계 초과면 인덱싱 개선 착수 신호)
        if (elapsed > 200L) {
            log.warn("[키워드알림] 매칭 쿼리 지연 {}ms - type={}, matched={}", elapsed, doc.getType(), matches.size());
        }
        if (matches.isEmpty()) {
            return List.of();
        }

        // 4. 회원 단위로 묶기(콘텐츠당 1회). 같은 회원의 여러 키워드 매칭 중 첫 건을 대표로 사용.
        Map<Long, KeywordMatch> representativeByMember = new LinkedHashMap<>();
        for (KeywordMatch match : matches) {
            representativeByMember.putIfAbsent(match.memberId(), match);
        }

        // 5. 회원마다 dedup -> 내역 저장 -> 기기 토큰 수집
        List<FcmDispatch> dispatches = new ArrayList<>();
        for (KeywordMatch match : representativeByMember.values()) {
            if (!claimDedup(match.memberId(), searchIndexId)) {
                continue; // Redis 가 이미 발송됨을 확인 -> 스킵
            }
            try {
                Member memberRef = memberRepository.getReferenceById(match.memberId());
                NotificationHistory history = notificationHistoryRepository.saveAndFlush(
                        NotificationHistory.of(memberRef, match.subscriptionId(), match.keyword(),
                                searchIndexId, doc.getTitle(), doc.getLink(), doc.getType()));

                List<String> tokens = deviceTokenRepository.findByMember(memberRef).stream()
                        .map(DeviceToken::getFcmToken)
                        .toList();
                if (!tokens.isEmpty()) {
                    dispatches.add(new FcmDispatch(tokens, match.keyword(), doc.getTitle(),
                            buildData(doc, searchIndexId, history.getId())));
                }
            } catch (DataIntegrityViolationException e) {
                // (회원, 콘텐츠) 유일 제약 위반 = 이미 발송됨(권위 dedup). 안전하게 스킵.
                log.debug("중복 알림 스킵 - memberId={}, searchIndexId={}", match.memberId(), searchIndexId);
            }
        }
        return dispatches;
    }

    /**
     * Redis 빠른 dedup. 처음이면 true(진행), 이미 claim 됐으면 false(스킵).
     * Redis 장애 시에는 true 를 반환해 진행하고, DB 유일 제약(권위 dedup)에 맡긴다.
     */
    private boolean claimDedup(Long memberId, String searchIndexId) {
        String key = DEDUP_KEY_PREFIX + memberId + ":" + searchIndexId;
        try {
            Boolean firstTime = keywordRedisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
            return !Boolean.FALSE.equals(firstTime);
        } catch (Exception e) {
            log.warn("Redis dedup 실패, DB 제약으로 진행 - key={}", key, e);
            return true;
        }
    }

    private Map<String, String> buildData(SearchDocument doc, String searchIndexId, Long historyId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", nz(doc.getType()));
        data.put("searchIndexId", searchIndexId);
        data.put("link", nz(doc.getLink()));
        data.put("historyId", String.valueOf(historyId));
        return data;
    }

    private String buildSearchIndexId(String type, String originalId) {
        return nz(type).trim() + ":" + nz(originalId).trim();
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }
}
