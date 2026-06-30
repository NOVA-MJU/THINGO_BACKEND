package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.NotificationHistoryDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.NotificationHistoryNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함(발송 내역) 조회/읽음 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationHistoryService {

    private final NotificationHistoryRepository notificationHistoryRepository;
    private final MemberRepository memberRepository;

    /** 내 알림 내역(최신 발송순) 페이지 조회 */
    public Page<NotificationHistoryDTO.Response.Detail> getMyNotifications(String email, Pageable pageable) {
        Member member = findMember(email);
        return notificationHistoryRepository.findByMemberOrderBySentAtDesc(member, pageable)
                .map(NotificationHistoryDTO.Response.Detail::from);
    }

    /** 단건 읽음 처리 */
    @Transactional
    public void markAsRead(String email, Long notificationId) {
        Member member = findMember(email);
        NotificationHistory history = notificationHistoryRepository.findByIdAndMember(notificationId, member)
                .orElseThrow(NotificationHistoryNotFoundException::new);
        history.markAsRead();
    }

    /** 전체 읽음 처리, 변경 건수 반환 */
    @Transactional
    public int markAllAsRead(String email) {
        Member member = findMember(email);
        return notificationHistoryRepository.markAllAsRead(member);
    }

    private Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }
}
