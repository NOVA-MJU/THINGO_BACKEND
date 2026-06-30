package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.KeywordSubscriptionDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.keywordAlarm.exception.DuplicateKeywordException;
import nova.mjs.domain.thingo.keywordAlarm.exception.KeywordSubscriptionNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.KeywordSubscriptionRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 키워드 알림 구독 서비스.
 *
 * 화면 06-2-3(키워드 알림 설정)의 등록/조회/수정/삭제를 담당한다.
 * 발송(매칭/푸시)은 별도 서비스(KeywordMatchingService)에서 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordSubscriptionService {

    /** 화면 6번 추천 키워드(고정값). 운영 변경 여지를 위해 API 로 노출한다. */
    private static final List<String> RECOMMENDED_KEYWORDS =
            List.of("중간고사", "기말고사", "해외탐방", "해외봉사", "수강신청");

    private final KeywordSubscriptionRepository keywordSubscriptionRepository;
    private final MemberRepository memberRepository;

    /**
     * 키워드 알림을 등록한다.
     *
     * @param email   현재 로그인 회원 이메일
     * @param request 키워드 + 카테고리(1개 이상)
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.Detail create(String email, KeywordSubscriptionDTO.Request.Create request) {
        // 1. 회원 조회
        Member member = findMember(email);

        // 2. 키워드 정규화(앞뒤 공백 제거). @Pattern 으로 내부 공백은 이미 차단됨.
        String keyword = request.getKeyword().trim();
        Set<AlarmCategory> categories = request.getCategories();

        // 3. 같은 키워드 중복 등록 차단
        if (keywordSubscriptionRepository.existsByMemberAndKeyword(member, keyword)) {
            throw new DuplicateKeywordException();
        }

        // 4. 저장
        KeywordSubscription saved = keywordSubscriptionRepository.save(
                KeywordSubscription.of(member, keyword, categories));
        log.info("키워드 알림 등록 완료 - email={}, keyword={}, categories={}", email, keyword, categories);

        return KeywordSubscriptionDTO.Response.Detail.from(saved);
    }

    /**
     * 회원의 키워드 알림 목록을 최신 등록순으로 조회한다.
     */
    public List<KeywordSubscriptionDTO.Response.Detail> getMySubscriptions(String email) {
        // 1. 회원 조회
        Member member = findMember(email);

        // 2. 구독 목록 조회 후 응답 DTO 변환
        return keywordSubscriptionRepository.findByMemberOrderByIdDesc(member).stream()
                .map(KeywordSubscriptionDTO.Response.Detail::from)
                .toList();
    }

    /**
     * 구독의 알림 카테고리를 교체한다.
     */
    @Transactional
    public KeywordSubscriptionDTO.Response.Detail updateCategories(
            String email, Long subscriptionId, KeywordSubscriptionDTO.Request.UpdateCategories request) {
        // 1. 회원 + 소유 구독 조회(소유권 동시 검증)
        Member member = findMember(email);
        KeywordSubscription subscription = findOwnedSubscription(subscriptionId, member);

        // 2. 카테고리 교체
        subscription.updateCategories(request.getCategories());
        log.info("키워드 알림 카테고리 수정 - email={}, id={}, categories={}", email, subscriptionId, request.getCategories());

        return KeywordSubscriptionDTO.Response.Detail.from(subscription);
    }

    /**
     * 구독을 삭제한다.
     */
    @Transactional
    public void delete(String email, Long subscriptionId) {
        // 1. 회원 + 소유 구독 조회(소유권 동시 검증)
        Member member = findMember(email);
        KeywordSubscription subscription = findOwnedSubscription(subscriptionId, member);

        // 2. 삭제
        keywordSubscriptionRepository.delete(subscription);
        log.info("키워드 알림 삭제 - email={}, id={}", email, subscriptionId);
    }

    /**
     * 추천 키워드(고정값) 목록.
     */
    public List<String> getRecommendedKeywords() {
        return RECOMMENDED_KEYWORDS;
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }

    private KeywordSubscription findOwnedSubscription(Long subscriptionId, Member member) {
        return keywordSubscriptionRepository.findByIdAndMember(subscriptionId, member)
                .orElseThrow(KeywordSubscriptionNotFoundException::new);
    }
}
