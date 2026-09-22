package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 학식(방송형) 알림 서비스.
 *
 * 키워드 매칭과 달리, '학식' 카테고리를 구독한 회원 전원에게 "새 학식 등록" 알림을 1건씩 보낸다.
 * 학식 크롤링은 같은 주를 여러 번 받아오므로, 내용 지문(signature)이 직전과 같으면 발송을 건너뛴다.
 */
@Slf4j
@Service
public class CafeteriaAlarmService {

    private static final String SIGNATURE_KEY = "kwalarm:cafeteria:signature";
    private static final String ALARM_TITLE = "새로운 학식 메뉴가 등록되었어요";
    private static final String MATCHED_LABEL = "학식";
    private static final String TYPE = AlarmCategory.CAFETERIA.getSearchType(); // "WEEKLY_MENU"

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final RedisTemplate<String, String> keywordRedisTemplate;
    private final AlarmMetrics alarmMetrics;

    public CafeteriaAlarmService(KeywordSubscriptionRepository keywordSubscriptionRepository,
                                 NotificationHistoryRepository notificationHistoryRepository,
                                 DeviceTokenRepository deviceTokenRepository,
                                 @Qualifier("keywordRedisTemplate") RedisTemplate<String, String> keywordRedisTemplate,
                                 AlarmMetrics alarmMetrics) {
        this.keywordSubscriptionRepository = keywordSubscriptionRepository;
        this.notificationHistoryRepository = notificationHistoryRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.keywordRedisTemplate = keywordRedisTemplate;
        this.alarmMetrics = alarmMetrics;
    }

    /**
     * 새 학식이면 구독자 전원에게 알림 내역을 적재하고 발송 대상을 모은다.
     *
     * @param menuCount 저장된 식단 수(표시용)
     * @param signature 식단 내용 지문(직전과 같으면 발송 생략)
     * @return 회원별 FCM 발송 단위 목록
     */
    // 호출부(WeeklyMenuAlarmListener)가 AFTER_COMMIT 단계 -> 완료된 트랜잭션에 참여하면 저장이 실패한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<FcmDispatch> broadcastIfNew(int menuCount, String signature) {
        // 1. 변경 감지: 직전 지문과 같으면(같은 주 반복 크롤링) 알림 생략.
        //    지문 갱신은 내역 적재가 끝난 뒤에 한다(먼저 갱신하면 저장 실패 시 그 주 학식은 영영 못 나간다).
        if (isSameAsLastSignature(signature)) {
            log.info("[학식알림] 내용 변경 없음 - 발송 생략");
            return List.of();
        }

        // 2. 학식 카테고리 구독 조회 후 회원 단위로 묶음(한 회원이 학식 구독을 여러 개 둬도 1건)
        List<KeywordSubscription> subscriptions =
                keywordSubscriptionRepository.findByCategoryWithMember(AlarmCategory.CAFETERIA);
        Map<Long, KeywordSubscription> representativeByMember = new LinkedHashMap<>();
        for (KeywordSubscription subscription : subscriptions) {
            representativeByMember.putIfAbsent(subscription.getMember().getId(), subscription);
        }
        if (representativeByMember.isEmpty()) {
            return List.of();
        }

        // 3. searchIndexId 에 지문을 포함 -> (회원, 지문) 유일 제약이 권위 dedup 역할
        String searchIndexId = TYPE + ":" + signature;

        // 4. 회원마다 내역 저장 + 기기 토큰 수집
        List<FcmDispatch> dispatches = new ArrayList<>();
        for (KeywordSubscription subscription : representativeByMember.values()) {
            Member member = subscription.getMember();
            try {
                NotificationHistory history = notificationHistoryRepository.saveAndFlush(
                        NotificationHistory.of(member, subscription.getId(), MATCHED_LABEL,
                                searchIndexId, ALARM_TITLE, null, TYPE));

                List<String> tokens = deviceTokenRepository.findByMember(member).stream()
                        .map(DeviceToken::getFcmToken)
                        .toList();
                if (!tokens.isEmpty()) {
                    dispatches.add(new FcmDispatch(tokens, MATCHED_LABEL, ALARM_TITLE,
                            buildData(searchIndexId, menuCount, history.getId())));
                }
            } catch (DataIntegrityViolationException e) {
                // (회원, 지문) 유일 제약 위반 = 이미 발송됨. 안전하게 스킵.
                log.debug("학식 중복 알림 스킵 - memberId={}, signature={}", member.getId(), signature);
            } catch (RuntimeException e) {
                alarmMetrics.failed("cafeteria");
                log.error("[학식알림] 내역 저장 실패 - memberId={}, signature={}", member.getId(), signature, e);
                throw e; // 지문을 갱신하지 않고 빠져나가 다음 크롤에서 재시도되게 한다
            }
        }

        // 5. 여기까지 왔으면 적재 성공 -> 지문 갱신(같은 주 반복 크롤링 무시)
        markSignature(signature);
        alarmMetrics.dispatched(dispatches.size());
        log.info("[학식알림] 발송 단위 {}건 - menuCount={}", dispatches.size(), menuCount);
        return dispatches;
    }

    /**
     * 직전 지문과 같은지만 본다(갱신하지 않음). Redis 장애 시에는 false(=진행)를 반환하고
     * (회원, 지문) DB 유일 제약에 dedup 을 맡긴다.
     */
    private boolean isSameAsLastSignature(String signature) {
        try {
            return signature.equals(keywordRedisTemplate.opsForValue().get(SIGNATURE_KEY));
        } catch (Exception e) {
            log.warn("Redis signature 비교 실패, DB 제약으로 진행", e);
            return false;
        }
    }

    /** 내역 적재가 끝난 뒤에만 호출한다. */
    private void markSignature(String signature) {
        try {
            keywordRedisTemplate.opsForValue().set(SIGNATURE_KEY, signature);
        } catch (Exception e) {
            log.warn("Redis signature 갱신 실패 - DB 제약이 중복을 막는다", e);
        }
    }

    private Map<String, String> buildData(String searchIndexId, int menuCount, Long historyId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", TYPE);
        data.put("searchIndexId", searchIndexId);
        data.put("menuCount", String.valueOf(menuCount));
        data.put("historyId", String.valueOf(historyId));
        return data;
    }
}
