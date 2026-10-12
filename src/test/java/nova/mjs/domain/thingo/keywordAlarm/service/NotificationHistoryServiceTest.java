package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.keywordAlarm.dto.NotificationHistoryDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    private NotificationHistoryService service;
    private Member member;
    private AlarmOwner owner;

    @BeforeEach
    void setUp() {
        service = new NotificationHistoryService(notificationHistoryRepository);
        member = Member.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .email("user@mju.ac.kr")
                .build();
        owner = AlarmOwner.ofMember(member);
    }

    @Test
    @DisplayName("알림함은 화면 카테고리와 전체 미읽음 상태를 함께 반환한다")
    void getMyNotifications() {
        var pageable = PageRequest.of(0, 20);
        NotificationHistory history = notification("NOTICE", "장학");
        given(notificationHistoryRepository.findInbox(member, pageable))
                .willReturn(new PageImpl<>(List.of(history), pageable, 1));
        given(notificationHistoryRepository.countByMemberAndReadFalse(member)).willReturn(3L);

        NotificationHistoryDTO.Response.Inbox inbox =
                service.getMyNotifications(owner, pageable);

        assertThat(inbox.getUnreadCount()).isEqualTo(3);
        assertThat(inbox.isHasUnread()).isTrue();
        assertThat(inbox.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getCategoryCode()).isEqualTo("NOTICE");
            assertThat(item.getCategory()).isEqualTo("공지사항");
            assertThat(item.getKeyword()).isEqualTo("장학");
            assertThat(item.isRead()).isFalse();
        });
    }

    @Test
    @DisplayName("메인 화면용 미읽음 상태는 목록 조회 없이 개수와 존재 여부만 반환한다")
    void getUnreadStatus() {
        given(notificationHistoryRepository.countByMemberAndReadFalse(member)).willReturn(2L);

        var status = service.getUnreadStatus(owner);

        assertThat(status.getUnreadCount()).isEqualTo(2);
        assertThat(status.isHasUnread()).isTrue();
    }

    @Test
    @DisplayName("학식 방송형 알림은 키워드를 노출하지 않는다")
    void cafeteriaDoesNotExposeKeyword() {
        var pageable = PageRequest.of(0, 20);
        NotificationHistory history = notification("WEEKLY_MENU", "학식");
        given(notificationHistoryRepository.findInbox(member, pageable))
                .willReturn(new PageImpl<>(List.of(history), pageable, 1));
        given(notificationHistoryRepository.countByMemberAndReadFalse(member)).willReturn(1L);

        var item = service.getMyNotifications(owner, pageable)
                .getContent().get(0);

        assertThat(item.getCategoryCode()).isEqualTo("CAFETERIA");
        assertThat(item.getCategory()).isEqualTo("학식");
        assertThat(item.getKeyword()).isNull();
    }

    @Test
    @DisplayName("명지도 리뷰 좋아요는 명지도 카테고리이며 키워드가 없다")
    void reviewLikeCategory() {
        var pageable = PageRequest.of(0, 20);
        NotificationHistory history = NotificationHistory.ofActivity(
                member,
                "REVIEW_LIKE:" + UUID.randomUUID(),
                "사용자님이 리뷰를 좋아합니다.",
                "/reviews/1",
                "REVIEW_LIKE"
        );
        given(notificationHistoryRepository.findInbox(member, pageable))
                .willReturn(new PageImpl<>(List.of(history), pageable, 1));
        given(notificationHistoryRepository.countByMemberAndReadFalse(member)).willReturn(1L);

        var item = service.getMyNotifications(owner, pageable)
                .getContent().get(0);

        assertThat(item.getCategoryCode()).isEqualTo("MAP");
        assertThat(item.getCategory()).isEqualTo("명지도");
        assertThat(item.getKeyword()).isNull();
    }

    @Test
    @DisplayName("단건 읽음 처리 응답은 read true를 반환한다")
    void markAsRead() {
        NotificationHistory history = notification("COMMUNITY", "축제");
        given(notificationHistoryRepository.findByIdAndMember(10L, member))
                .willReturn(Optional.of(history));

        var result = service.markAsRead(owner, 10L);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    @DisplayName("모두 읽음 처리 후에는 미읽음 상태가 없다")
    void markAllAsRead() {
        given(notificationHistoryRepository.markAllAsRead(member)).willReturn(4);

        var result = service.markAllAsRead(owner);

        assertThat(result.getUpdatedCount()).isEqualTo(4);
        assertThat(result.getUnreadCount()).isZero();
        assertThat(result.isHasUnread()).isFalse();
    }

    @Test
    @DisplayName("비로그인 기기 알림함은 기기 소유 알림과 미읽음 수만 조회한다")
    void getDeviceNotifications() {
        var pageable = PageRequest.of(0, 20);
        UUID deviceId = UUID.randomUUID();
        NotificationHistory history = NotificationHistory.of(AlarmOwner.ofDevice(deviceId), 1L, "장학",
                "NOTICE:1", "알림 제목", "/target/1", "NOTICE");
        given(notificationHistoryRepository.findDeviceInbox(deviceId, pageable))
                .willReturn(new PageImpl<>(List.of(history), pageable, 1));
        given(notificationHistoryRepository.countByDeviceIdAndReadFalse(deviceId)).willReturn(1L);

        var inbox = service.getMyNotifications(AlarmOwner.ofDevice(deviceId), pageable);

        assertThat(inbox.getUnreadCount()).isEqualTo(1);
        assertThat(inbox.getContent()).singleElement()
                .satisfies(item -> assertThat(item.getKeyword()).isEqualTo("장학"));
    }

    @Test
    @DisplayName("비로그인 기기는 자기 알림만 읽음 처리할 수 있다")
    void markAsReadByDevice() {
        UUID deviceId = UUID.randomUUID();
        given(notificationHistoryRepository.findByIdAndDeviceId(10L, deviceId)).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.markAsRead(AlarmOwner.ofDevice(deviceId), 10L))
                .isInstanceOf(nova.mjs.domain.thingo.keywordAlarm.exception.NotificationHistoryNotFoundException.class);
    }

    private NotificationHistory notification(String type, String keyword) {
        return NotificationHistory.of(
                member,
                1L,
                keyword,
                type + ":1",
                "알림 제목",
                "/target/1",
                type
        );
    }
}
