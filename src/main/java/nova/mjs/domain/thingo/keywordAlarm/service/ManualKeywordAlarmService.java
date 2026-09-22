package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.ManualAlarmDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.AlarmSourceNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.exception.DeviceTokenNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmDispatch;
import nova.mjs.domain.thingo.keywordAlarm.service.fcm.FcmSender;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.domain.thingo.search.entity.UnifiedSearchIndex;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 수동 키워드 알림 발송 서비스.
 *
 * 자동 키워드 매칭(KeywordMatchingService)과 달리, 새 콘텐츠 유입이 아니라
 * "특정 회원 + 특정 키워드"로 과거에 색인된 콘텐츠 1건을 골라 즉시 FCM 푸시를 보낸다.
 * (예: 운영 점검용으로 '해외봉사' 구독자에게 해당 키워드의 과거 공지 1건을 재발송)
 *
 * 키워드는 대상 회원이 실제로 등록(enabled)한 것이어야 한다. 점검용이라도 구독하지 않은 키워드로
 * 보내면 사용자 알림함에 본인이 등록한 적 없는 키워드의 알림이 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualKeywordAlarmService {

    private final MemberRepository memberRepository;
    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final UnifiedSearchIndexRepository unifiedSearchIndexRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final FcmSender fcmSender;

    /**
     * 대상 회원에게 키워드 매칭 과거 콘텐츠 1건을 FCM 으로 발송한다.
     *
     * @throws MemberNotFoundException     대상 이메일의 회원이 없음
     * @throws KeywordSubscriptionNotFoundException 대상이 그 키워드를 등록하지 않았거나 알림을 꺼둠
     * @throws AlarmSourceNotFoundException 키워드에 매칭되는 활성 콘텐츠가 없음
     * @throws DeviceTokenNotFoundException 대상 회원의 등록 기기 토큰이 없음(보낼 곳이 없음)
     */
    @Transactional
    public ManualAlarmDTO.Response send(String email, String rawKeyword, String searchIndexId) {
        String keyword = rawKeyword.trim();

        // 1. 대상 회원
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);

        // 2. 대상이 실제로 등록한 키워드만 허용한다.
        //    (운영 점검용이라도 구독하지 않은 키워드로 보내면 사용자 알림함에 남의 키워드가 쌓인다)
        KeywordSubscription subscription = keywordSubscriptionRepository
                .findByMemberOrderByIdDesc(member).stream()
                .filter(KeywordSubscription::isEnabled)
                .filter(candidate -> normalize(candidate.getKeyword()).equals(normalize(keyword)))
                .findFirst()
                .orElseThrow(KeywordSubscriptionNotFoundException::new);

        // 3. 발송할 과거 콘텐츠 1건.
        //    - searchIndexId 를 주면 그 콘텐츠를 정확히 지정(마케팅: 특정 캠페인 공지 선택).
        //    - 없으면 키워드가 제목에 포함된 활성 콘텐츠 중 최신 1건 자동 선택.
        UnifiedSearchIndex doc = resolveSource(searchIndexId, keyword);

        // 4. 대상 회원의 기기 토큰(없으면 보낼 곳이 없음)
        List<String> tokens = deviceTokenRepository.findByMember(member).stream()
                .map(DeviceToken::getFcmToken)
                .toList();
        if (tokens.isEmpty()) {
            throw new DeviceTokenNotFoundException();
        }

        // 5. 알림함 기록(같은 회원+콘텐츠는 유일 제약 -> 이미 있으면 그 기록을 재사용해 재발송 허용)
        NotificationHistory history = notificationHistoryRepository
                .findByMemberAndSearchIndexId(member, doc.getId())
                .orElseGet(() -> notificationHistoryRepository.saveAndFlush(
                        NotificationHistory.of(member, subscription.getId(), subscription.getKeyword(),
                                doc.getId(), doc.getTitle(), doc.getLink(), doc.getType())));

        // 6. FCM 발송(키워드 알림 스타일: "'키워드' 키워드 새 소식" / 본문=콘텐츠 제목). @Async 로 비동기 처리.
        FcmDispatch dispatch = new FcmDispatch(tokens, keyword, doc.getTitle(),
                buildData(doc, history.getId()));
        fcmSender.sendAll(dispatch);

        log.info("[수동알림] 발송 요청 - email={}, keyword={}, searchIndexId={}, tokenCount={}",
                email, keyword, doc.getId(), tokens.size());

        return ManualAlarmDTO.Response.of(email, keyword, doc.getId(), doc.getTitle(),
                doc.getType(), doc.getLink(), history.getId(), tokens.size());
    }

    /**
     * 발송할 과거 콘텐츠를 결정한다.
     * searchIndexId 지정 시 해당 콘텐츠(활성)를 그대로 사용하고, 없으면 키워드 최신 매칭으로 자동 선택한다.
     */
    private UnifiedSearchIndex resolveSource(String searchIndexId, String keyword) {
        if (searchIndexId != null && !searchIndexId.isBlank()) {
            UnifiedSearchIndex doc = unifiedSearchIndexRepository.findById(searchIndexId.trim())
                    .orElseThrow(AlarmSourceNotFoundException::new);
            if (!Boolean.TRUE.equals(doc.getActive())) {
                throw new AlarmSourceNotFoundException();
            }
            return doc;
        }
        return unifiedSearchIndexRepository
                .findLatestActiveByTitleKeyword(escapeLike(keyword), contentSearchTypes())
                .orElseThrow(AlarmSourceNotFoundException::new);
    }

    /** 알림 대상 카테고리의 통합검색 type(학식/WEEKLY_MENU 은 인덱스에 없어 제외). */
    private List<String> contentSearchTypes() {
        return Arrays.stream(AlarmCategory.values())
                .filter(category -> category != AlarmCategory.CAFETERIA)
                .map(AlarmCategory::getSearchType)
                .toList();
    }

    private Map<String, String> buildData(UnifiedSearchIndex doc, Long historyId) {
        Map<String, String> data = new HashMap<>();
        data.put("type", nz(doc.getType()));
        data.put("searchIndexId", nz(doc.getId()));
        data.put("link", nz(doc.getLink()));
        data.put("historyId", String.valueOf(historyId));
        return data;
    }

    /** LIKE 패턴 메타문자를 리터럴로 escape('\\'). 쿼리의 ESCAPE '\\' 와 짝을 이룬다. */
    private String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** 키워드 비교용 정규화: 공백/기호 제거 + 소문자 */
    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-zA-Z가-힣]", "").toLowerCase();
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }
}
