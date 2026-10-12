package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.NotificationHistoryDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.exception.NotificationHistoryNotFoundException;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함(발송 내역) 조회/읽음 서비스.
 * 소유자는 로그인 회원 또는 비로그인 기기(AlarmOwner).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationHistoryService {

    private final NotificationHistoryRepository notificationHistoryRepository;

    /** 내 알림 내역(미읽음 최신순 -> 읽음 최신순)과 전체 미읽음 수 조회 */
    public NotificationHistoryDTO.Response.Inbox getMyNotifications(AlarmOwner owner, Pageable pageable) {
        Page<NotificationHistory> page = owner.isMember()
                ? notificationHistoryRepository.findInbox(owner.member(), pageable)
                : notificationHistoryRepository.findDeviceInbox(owner.deviceId(), pageable);
        return NotificationHistoryDTO.Response.Inbox.of(
                page.map(NotificationHistoryDTO.Response.Detail::from), countUnread(owner));
    }

    /** 메인 화면 알림 배지용 경량 미읽음 상태 조회 */
    public NotificationHistoryDTO.Response.UnreadStatus getUnreadStatus(AlarmOwner owner) {
        return NotificationHistoryDTO.Response.UnreadStatus.of(countUnread(owner));
    }

    /** 단건 읽음 처리 후 갱신된 알림 반환. 이미 읽은 알림에도 멱등이다. */
    @Transactional
    public NotificationHistoryDTO.Response.Detail markAsRead(AlarmOwner owner, Long notificationId) {
        NotificationHistory history = (owner.isMember()
                ? notificationHistoryRepository.findByIdAndMember(notificationId, owner.member())
                : notificationHistoryRepository.findByIdAndDeviceId(notificationId, owner.deviceId()))
                .orElseThrow(NotificationHistoryNotFoundException::new);
        history.markAsRead();
        return NotificationHistoryDTO.Response.Detail.from(history);
    }

    /** 전체 읽음 처리 결과 반환 */
    @Transactional
    public NotificationHistoryDTO.Response.ReadAllResult markAllAsRead(AlarmOwner owner) {
        int updated = owner.isMember()
                ? notificationHistoryRepository.markAllAsRead(owner.member())
                : notificationHistoryRepository.markAllAsReadByDevice(owner.deviceId());
        return NotificationHistoryDTO.Response.ReadAllResult.of(updated);
    }

    private long countUnread(AlarmOwner owner) {
        return owner.isMember()
                ? notificationHistoryRepository.countByMemberAndReadFalse(owner.member())
                : notificationHistoryRepository.countByDeviceIdAndReadFalse(owner.deviceId());
    }
}
