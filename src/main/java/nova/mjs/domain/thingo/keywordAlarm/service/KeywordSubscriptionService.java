package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.DuplicateKeywordException;
import nova.mjs.domain.thingo.keywordAlarm.exception.InvalidAlarmTopicException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionLimitExceededException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.semantic.TopicCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 키워드 알림 구독 서비스.
 *
 * 화면 06-2-3(키워드 알림 설정)의 등록/조회/수정/삭제를 담당한다.
 * 소유자는 로그인 회원 또는 비로그인 기기(AlarmOwner)이며, 로그인 시 기기 구독을 회원으로 병합한다.
 * 발송(매칭/푸시)은 별도 서비스(KeywordMatchingService)에서 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordSubscriptionService {

    /** 화면 6번 추천 키워드(고정값). 운영 변경 여지를 위해 API 로 노출한다. */
    private static final List<String> RECOMMENDED_KEYWORDS =
            List.of("수강신청", "기숙사", "졸업", "국가근로", "해외탐방", "해외봉사");

    /** 소유자(회원/기기)당 최대 구독 수. 앱 화면 제한과 같고, 우회 요청으로 매칭 비용이 커지는 것을 막는다. */
    static final int MAX_SUBSCRIPTIONS = 10;

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final TopicCatalog topicCatalog;

    /**
     * 키워드 알림을 등록한다.
     *
     * @param owner   로그인 회원 또는 비로그인 기기
     * @param request 키워드 + 카테고리(1개 이상)
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.Detail create(AlarmOwner owner, KeywordSubscriptionDTO.Request.Create request) {
        // 1. 키워드 정규화(앞뒤 공백 제거). @Pattern 으로 내부 공백은 이미 차단됨.
        String keyword = request.getKeyword().trim();
        String topicId = validateTopicId(request.getTopicId());
        Set<AlarmCategory> categories = request.getCategories();

        // 2. 같은 키워드 중복 등록 차단
        if (existsKeyword(owner, keyword)) {
            throw new DuplicateKeywordException();
        }

        // 3. 소유자당 최대 개수 제한
        if (countSubscriptions(owner) >= MAX_SUBSCRIPTIONS) {
            throw new KeywordSubscriptionLimitExceededException();
        }

        // 4. 저장
        KeywordSubscription saved = keywordSubscriptionRepository.save(
                KeywordSubscription.of(owner, keyword, topicId, categories));
        log.info("키워드 알림 등록 완료 - owner={}, keyword={}, topicId={}, categories={}",
                owner, keyword, topicId, categories);

        return KeywordSubscriptionDTO.Response.Detail.from(saved);
    }

    /**
     * 소유자의 키워드 알림 목록을 최신 등록순으로 조회한다.
     */
    public List<KeywordSubscriptionDTO.Response.Detail> getMySubscriptions(AlarmOwner owner) {
        List<KeywordSubscription> subscriptions = owner.isMember()
                ? keywordSubscriptionRepository.findByMemberOrderByIdDesc(owner.member())
                : keywordSubscriptionRepository.findByDeviceIdOrderByIdDesc(owner.deviceId());
        return subscriptions.stream()
                .map(KeywordSubscriptionDTO.Response.Detail::from)
                .toList();
    }

    /**
     * 구독의 키워드와 알림 카테고리를 교체한다.
     *
     * INSERT-only 매칭이라 키워드 변경은 과거 콘텐츠에 소급되지 않고 이후 신규 콘텐츠부터 적용된다.
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.Detail update(
            AlarmOwner owner, Long subscriptionId, KeywordSubscriptionDTO.Request.Update request) {
        // 1. 소유 구독 조회(소유권 동시 검증)
        KeywordSubscription subscription = findOwnedSubscription(subscriptionId, owner);

        // 2. 키워드 정규화(앞뒤 공백 제거). @Pattern 으로 내부 공백은 이미 차단됨.
        String keyword = request.getKeyword().trim();
        String topicId = validateTopicId(request.getTopicId());

        // 3. 키워드가 바뀌는 경우에만 (소유자, keyword) 중복 재검사(자기 자신은 그대로 통과)
        if (!keyword.equals(subscription.getKeyword()) && existsKeyword(owner, keyword)) {
            throw new DuplicateKeywordException();
        }

        // 4. 키워드 + 카테고리 교체
        subscription.changeKeyword(keyword);
        subscription.changeTopicId(topicId);
        subscription.updateCategories(request.getCategories());
        log.info("키워드 알림 수정 - owner={}, id={}, keyword={}, topicId={}, categories={}",
                owner, subscriptionId, keyword, topicId, request.getCategories());

        return KeywordSubscriptionDTO.Response.Detail.from(subscription);
    }

    /**
     * 구독의 알림 on/off 를 변경한다. off 면 발송 경로에서 제외되고, 구독과 과거 알림 내역은 유지된다.
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.Detail updateEnabled(
            AlarmOwner owner, Long subscriptionId, KeywordSubscriptionDTO.Request.UpdateEnabled request) {
        // 1. 소유 구독 조회(소유권 동시 검증)
        KeywordSubscription subscription = findOwnedSubscription(subscriptionId, owner);

        // 2. on/off 반영
        subscription.changeEnabled(request.getEnabled());
        log.info("키워드 알림 on/off 변경 - owner={}, id={}, enabled={}", owner, subscriptionId, request.getEnabled());

        return KeywordSubscriptionDTO.Response.Detail.from(subscription);
    }

    /**
     * 구독을 삭제한다.
     */
    @Transactional
    public void delete(AlarmOwner owner, Long subscriptionId) {
        // 1. 소유 구독 조회(소유권 동시 검증)
        KeywordSubscription subscription = findOwnedSubscription(subscriptionId, owner);

        // 2. 삭제
        keywordSubscriptionRepository.delete(subscription);
        log.info("키워드 알림 삭제 - owner={}, id={}", owner, subscriptionId);
    }

    /**
     * 로그인 직후, 비로그인 상태에서 이 기기로 등록한 구독과 알림함을 회원으로 옮긴다.
     *
     * - 회원에게 같은 키워드(앞뒤 공백 제거, 대소문자 무시)가 있으면 카테고리를 합치고 기기 구독은 지운다.
     * - 없으면 기기 구독의 소유자를 회원으로 바꾼다. 단 회원 구독이 최대 개수에 차면 더 옮기지 않는다
     *   (기기 쪽에 남겨 두고 skippedCount 로 알린다. 로그아웃하면 다시 게스트 구독으로 보인다).
     * - 알림함은 회원이 이미 받은 콘텐츠를 제외하고 옮기며, 겹치는 기기 알림은 지운다.
     * 기기에 남은 것이 없으면 아무 것도 바뀌지 않으므로 여러 번 호출해도 안전하다.
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.MergeResult mergeDevice(Member member, UUID deviceId) {
        // 1. 회원 구독을 정규화 키워드로 색인
        List<KeywordSubscription> memberSubscriptions = keywordSubscriptionRepository.findByMemberOrderByIdDesc(member);
        Map<String, KeywordSubscription> memberByKeyword = new HashMap<>();
        for (KeywordSubscription subscription : memberSubscriptions) {
            memberByKeyword.putIfAbsent(normalizeKeyword(subscription.getKeyword()), subscription);
        }
        int memberCount = memberSubscriptions.size();

        // 2. 기기 구독을 오래된 순서로 병합(제한에 걸리면 먼저 등록한 것부터 살린다)
        List<KeywordSubscription> deviceSubscriptions =
                new ArrayList<>(keywordSubscriptionRepository.findByDeviceIdOrderByIdDesc(deviceId));
        Collections.reverse(deviceSubscriptions);

        int merged = 0;
        int duplicate = 0;
        int skipped = 0;
        for (KeywordSubscription deviceSubscription : deviceSubscriptions) {
            String key = normalizeKeyword(deviceSubscription.getKeyword());
            KeywordSubscription existing = memberByKeyword.get(key);
            if (existing != null) {
                existing.absorb(deviceSubscription);
                keywordSubscriptionRepository.delete(deviceSubscription);
                duplicate++;
            } else if (memberCount >= MAX_SUBSCRIPTIONS) {
                skipped++;
            } else {
                deviceSubscription.reassignTo(member);
                memberByKeyword.put(key, deviceSubscription);
                memberCount++;
                merged++;
            }
        }

        // 3. 알림함 이동
        int movedNotifications = mergeNotifications(member, deviceId);

        log.info("기기 구독 병합 - memberId={}, deviceId={}, merged={}, duplicate={}, skipped={}, notifications={}",
                member.getId(), deviceId, merged, duplicate, skipped, movedNotifications);
        return KeywordSubscriptionDTO.Response.MergeResult.builder()
                .mergedCount(merged)
                .duplicateCount(duplicate)
                .skippedCount(skipped)
                .build();
    }

    /**
     * 추천 키워드(고정값) 목록.
     */
    public List<String> getRecommendedKeywords() {
        return RECOMMENDED_KEYWORDS;
    }

    /**
     * 기기 알림을 회원에게 옮긴다. (회원, 콘텐츠) 유일 제약이 있으므로 회원이 이미 받은 콘텐츠는 옮기지 않고 지운다.
     */
    private int mergeNotifications(Member member, UUID deviceId) {
        List<NotificationHistory> deviceHistories = notificationHistoryRepository.findByDeviceId(deviceId);
        if (deviceHistories.isEmpty()) {
            return 0;
        }
        Set<String> alreadyReceived = new HashSet<>(notificationHistoryRepository.findSearchIndexIdsByMember(
                member, deviceHistories.stream().map(NotificationHistory::getSearchIndexId).toList()));

        int moved = 0;
        for (NotificationHistory history : deviceHistories) {
            if (alreadyReceived.contains(history.getSearchIndexId())) {
                notificationHistoryRepository.delete(history);
            } else {
                history.reassignTo(member);
                moved++;
            }
        }
        return moved;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean existsKeyword(AlarmOwner owner, String keyword) {
        return owner.isMember()
                ? keywordSubscriptionRepository.existsByMemberAndKeyword(owner.member(), keyword)
                : keywordSubscriptionRepository.existsByDeviceIdAndKeyword(owner.deviceId(), keyword);
    }

    private long countSubscriptions(AlarmOwner owner) {
        return owner.isMember()
                ? keywordSubscriptionRepository.countByMember(owner.member())
                : keywordSubscriptionRepository.countByDeviceId(owner.deviceId());
    }

    private KeywordSubscription findOwnedSubscription(Long subscriptionId, AlarmOwner owner) {
        return (owner.isMember()
                ? keywordSubscriptionRepository.findByIdAndMember(subscriptionId, owner.member())
                : keywordSubscriptionRepository.findByIdAndDeviceId(subscriptionId, owner.deviceId()))
                .orElseThrow(KeywordSubscriptionNotFoundException::new);
    }

    private String validateTopicId(String topicId) {
        if (topicId == null || topicId.isBlank()) {
            return null;
        }
        String normalized = topicId.trim();
        var topic = topicCatalog.find(normalized).orElseThrow(InvalidAlarmTopicException::new);
        if (!topic.enabled() || !topic.subscribable()) {
            throw new InvalidAlarmTopicException();
        }
        return normalized;
    }
}
