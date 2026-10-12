package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.extern.slf4j.Slf4j;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.ElasticSearch.Document.SearchDocument;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordMatch;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.semantic.NoticeSemanticClassifier;
import nova.mjs.domain.thingo.semantic.NoticeSemanticMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 키워드 매칭 파이프라인.
 *
 * 신규 콘텐츠(SearchDocument)를 구독 키워드와 매칭해 알림 내역을 적재하고,
 * 발송 대상(기기 토큰 + 페이로드) 목록을 반환한다. 실제 FCM 발송은 호출부(리스너)가
 * 이 트랜잭션이 끝난 뒤 비동기로 수행한다(외부 호출을 트랜잭션 밖으로).
 *
 * 수신자는 회원 또는 비로그인 기기(AlarmOwner)다. 묶기/중복 차단/교차게시 판정은 모두 수신자 단위로 한다.
 */
@Slf4j
@Service
public class KeywordMatchingService {

    /** 같은 (구독, 콘텐츠) 재알림 차단 캐시 TTL */
    private static final Duration DEDUP_TTL = Duration.ofDays(7);
    private static final String DEDUP_KEY_PREFIX = "kwalarm:dedup:";

    /** 교차게시(같은 공지를 다른 게시판에 별도 글로 게시) 중복 판정 기간 */
    private static final Duration CROSS_POST_WINDOW = Duration.ofDays(3);

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;
    private final RedisTemplate<String, String> keywordRedisTemplate;
    private final NoticeSemanticClassifier noticeSemanticClassifier;
    private final AlarmMetrics alarmMetrics;

    public KeywordMatchingService(KeywordSubscriptionRepository keywordSubscriptionRepository,
                                  NotificationHistoryRepository notificationHistoryRepository,
                                  DeviceTokenRepository deviceTokenRepository,
                                  MemberRepository memberRepository,
                                  @Qualifier("keywordRedisTemplate") RedisTemplate<String, String> keywordRedisTemplate,
                                  NoticeSemanticClassifier noticeSemanticClassifier,
                                  AlarmMetrics alarmMetrics) {
        this.keywordSubscriptionRepository = keywordSubscriptionRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.memberRepository = memberRepository;
        this.keywordRedisTemplate = keywordRedisTemplate;
        this.noticeSemanticClassifier = noticeSemanticClassifier;
        this.alarmMetrics = alarmMetrics;
    }

    /**
     * 문서를 구독과 매칭해 알림 내역을 저장하고 발송 대상을 모은다.
     *
     * 한 콘텐츠가 한 수신자의 여러 키워드에 걸려도 알림은 1건(수신자 단위로 묶음, 대표 키워드 사용).
     *
     * @return 수신자별 FCM 발송 단위 목록 (기기 토큰이 없는 수신자는 제외)
     */
    // 호출부(KeywordAlarmIndexListener)가 AFTER_COMMIT 단계라 원본 트랜잭션은 이미 커밋된 상태다.
    // REQUIRED 로 두면 그 완료된 트랜잭션에 참여해 saveAndFlush 가
    // "no transaction is in progress" 로 실패한다(PgUnifiedSearchIndexListener 와 동일한 이유로 REQUIRES_NEW).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<FcmDispatch> matchAndCollect(SearchDocument doc) {
        // 1. MVP 카테고리(NOTICE/MJU_CALENDAR/COMMUNITY) 외 콘텐츠는 알림 대상 아님
        AlarmCategory category = AlarmCategory.fromSearchType(doc.getType()).orElse(null);
        if (category == null) {
            return List.of();
        }

        // 2. 제목만 토큰화(정확도 우선). 본문은 매칭에서 제외 -> "그 글의 주제"일 때만 알림.
        String docTokens = KomoranTokenizerUtil.buildSearchTokens(doc.getTitle());

        // 3. 자유 키워드는 제목 토큰, 표준 Topic은 공지 의미 분류 계층으로 매칭한다.
        long startedAt = System.currentTimeMillis();
        String searchIndexId = buildSearchIndexId(doc.getType(), doc.getId());
        List<KeywordMatch> matches = new ArrayList<>(
                keywordSubscriptionRepository.findMatchingSubscriptions(
                        category.name(), docTokens, doc.getTitle()));
        NoticeSemanticMetadata semantics = noticeSemanticClassifier.classify(
                doc.getTitle(), doc.getContent(), null);
        Set<String> expandedTopicIds = semantics.expandedTopicIds();
        if (!expandedTopicIds.isEmpty()) {
            matches.addAll(keywordSubscriptionRepository.findMatchingTopicSubscriptions(
                    category, expandedTopicIds));
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        // 관측 장치: 구독 폭증 시 매칭이 느려지는지 감시(임계 초과면 인덱싱 개선 착수 신호)
        if (elapsed > 200L) {
            log.warn("[키워드알림] 매칭 쿼리 지연 {}ms - type={}, matched={}", elapsed, doc.getType(), matches.size());
        }
        if (matches.isEmpty()) {
            return List.of();
        }

        // 4. 수신자(회원/기기) 단위로 묶기(콘텐츠당 1회). 같은 수신자의 여러 키워드 매칭 중 첫 건을 대표로 사용.
        Map<String, KeywordMatch> representativeByRecipient = new LinkedHashMap<>();
        for (KeywordMatch match : matches) {
            representativeByRecipient.putIfAbsent(AlarmOwner.key(match.memberId(), match.deviceId()), match);
        }

        // 5. 수신자마다 dedup -> 내역 저장 -> 기기 토큰 수집
        List<FcmDispatch> dispatches = new ArrayList<>();
        for (Map.Entry<String, KeywordMatch> entry : representativeByRecipient.entrySet()) {
            String recipientKey = entry.getKey();
            KeywordMatch match = entry.getValue();
            if (isCrossPostDuplicate(match, doc.getTitle())) {
                log.debug("교차게시 중복 스킵 - recipient={}, searchIndexId={}", recipientKey, searchIndexId);
                continue;
            }
            if (!claimDedup(recipientKey, searchIndexId)) {
                continue; // Redis 가 이미 발송됨을 확인 -> 스킵
            }
            try {
                AlarmOwner owner = match.memberId() != null
                        ? AlarmOwner.ofMember(memberRepository.getReferenceById(match.memberId()))
                        : AlarmOwner.ofDevice(match.deviceId());
                NotificationHistory history = notificationHistoryRepository.saveAndFlush(
                        NotificationHistory.of(owner, match.subscriptionId(), match.keyword(),
                                searchIndexId, doc.getTitle(),
                                NotificationLinks.resolve(doc.getType(), doc.getLink(), searchIndexId),
                                doc.getType()));

                List<String> tokens = findTokens(owner);
                if (!tokens.isEmpty()) {
                    dispatches.add(new FcmDispatch(tokens, match.keyword(), doc.getTitle(),
                            buildData(doc, searchIndexId, history.getId())));
                }
            } catch (DataIntegrityViolationException e) {
                // (수신자, 콘텐츠) 유일 제약 위반 = 이미 발송됨(권위 dedup). 안전하게 스킵.
                log.debug("중복 알림 스킵 - recipient={}, searchIndexId={}", recipientKey, searchIndexId);
            } catch (RuntimeException e) {
                // 저장 실패. claim 을 그대로 두면 TTL 7일 동안 재시도조차 막히므로 되돌린다.
                releaseDedup(recipientKey, searchIndexId);
                alarmMetrics.matchFailed();
                log.error("[키워드알림] 내역 저장 실패 - recipient={}, searchIndexId={}",
                        recipientKey, searchIndexId, e);
            }
        }
        alarmMetrics.dispatched(dispatches.size());
        return dispatches;
    }

    /**
     * 수신자의 FCM 토큰. 기기 수신자는 게스트 토큰(member 없음)만 - 그 기기가 로그인했다면
     * 토큰은 회원 소유로 넘어갔고 게스트 구독은 회원으로 병합됐어야 하므로 보내지 않는다.
     */
    private List<String> findTokens(AlarmOwner owner) {
        List<DeviceToken> tokens = owner.isMember()
                ? deviceTokenRepository.findByMember(owner.member())
                : deviceTokenRepository.findByDeviceIdAndMemberIsNull(owner.deviceId());
        return tokens.stream()
                .map(DeviceToken::getFcmToken)
                .toList();
    }

    /**
     * Redis 빠른 dedup. 처음이면 true(진행), 이미 claim 됐으면 false(스킵).
     * Redis 장애 시에는 true 를 반환해 진행하고, DB 유일 제약(권위 dedup)에 맡긴다.
     * 키: 회원 kwalarm:dedup:{memberId}:{searchIndexId}, 기기 kwalarm:dedup:d:{deviceId}:{searchIndexId}
     */
    private boolean claimDedup(String recipientKey, String searchIndexId) {
        String key = DEDUP_KEY_PREFIX + recipientKey + ":" + searchIndexId;
        try {
            Boolean firstTime = keywordRedisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_TTL);
            return !Boolean.FALSE.equals(firstTime);
        } catch (Exception e) {
            log.warn("Redis dedup 실패, DB 제약으로 진행 - key={}", key, e);
            return true;
        }
    }

    /**
     * 학교가 같은 공지를 여러 게시판에 별도 글로 올리면 링크/ID 가 달라 (수신자, 콘텐츠) dedup 에 걸리지 않는다.
     * 앞의 [부서명] 을 떼고 비교한 제목이 수신자가 최근 3일 안에 받은 알림과 같으면 같은 공지로 본다.
     * (예: "[진로취업지원팀] 선배와의 취업멘토링 모집" / "선배와의 취업멘토링 모집")
     */
    private boolean isCrossPostDuplicate(KeywordMatch match, String title) {
        String core = coreTitle(title);
        if (core.isEmpty()) {
            return false;
        }
        Instant since = Instant.now().minus(CROSS_POST_WINDOW);
        List<String> recentTitles = match.memberId() != null
                ? notificationHistoryRepository.findRecentTitles(match.memberId(), since)
                : notificationHistoryRepository.findRecentTitlesByDevice(match.deviceId(), since);
        return recentTitles.stream()
                .map(KeywordMatchingService::coreTitle)
                .anyMatch(core::equals);
    }

    /** 제목 비교 키: 앞머리 [..] 묶음 제거 + 영숫자/한글만 + 소문자 */
    static String coreTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replaceFirst("^\\s*(\\[[^\\]]*\\]\\s*)+", "")
                .replaceAll("[^0-9a-zA-Z가-힣]", "")
                .toLowerCase();
    }

    /** 저장이 실패한 claim 을 되돌린다(되돌리지 않으면 TTL 동안 재시도 불가). */
    private void releaseDedup(String recipientKey, String searchIndexId) {
        try {
            keywordRedisTemplate.delete(DEDUP_KEY_PREFIX + recipientKey + ":" + searchIndexId);
        } catch (Exception e) {
            log.warn("Redis dedup 해제 실패 - recipient={}, searchIndexId={}", recipientKey, searchIndexId, e);
        }
    }

    private Map<String, String> buildData(SearchDocument doc, String searchIndexId, Long historyId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", nz(doc.getType()));
        data.put("searchIndexId", searchIndexId);
        data.put("link", nz(NotificationLinks.resolve(doc.getType(), doc.getLink(), searchIndexId)));
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
