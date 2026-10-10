package nova.mjs.domain.thingo.keywordAlarm.service;

import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.event.ReviewLikePushRequestedEvent;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.review.entity.Review;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ActivityNotificationServiceTest {

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ActivityNotificationService service;
    private Member author;
    private Member actor;
    private CommunityBoard board;

    @BeforeEach
    void setUp() {
        service = new ActivityNotificationService(notificationHistoryRepository, eventPublisher);
        author = member(1L, "작성자");
        actor = member(2L, "좋아요맨");
        board = CommunityBoard.builder()
                .uuid(UUID.randomUUID())
                .title("열 글자가 넘는 게시글 제목입니다")
                .author(author)
                .build();
    }

    @Test
    @DisplayName("게시글 좋아요는 작성자에게 대상별 집계 알림으로 저장된다")
    void notifyCommunityLike() {
        String key = "COMMUNITY_LIKE:" + board.getUuid();
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(author, key))
                .willReturn(Optional.empty());

        service.notifyCommunityLike(board, actor, List.of(actor), 1);

        ArgumentCaptor<NotificationHistory> captor =
                ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository).save(captor.capture());
        NotificationHistory saved = captor.getValue();
        assertThat(saved.getMember()).isEqualTo(author);
        assertThat(saved.getType()).isEqualTo("COMMUNITY_LIKE");
        assertThat(saved.getTitle()).isEqualTo("좋아요맨님이 열 글자가 넘는 게… 글을 좋아합니다.");
        assertThat(saved.getLink()).isEqualTo("/posts/" + board.getUuid());
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("추가 좋아요는 기존 알림을 갱신하고 다시 미읽음 처리한다")
    void aggregateCommunityLike() {
        String key = "COMMUNITY_LIKE:" + board.getUuid();
        NotificationHistory existing = NotificationHistory.ofActivity(
                author, key, "이전 제목", "/boards/" + board.getUuid(), "COMMUNITY_LIKE");
        existing.markAsRead();
        Member secondActor = member(3L, "두번째");
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(author, key))
                .willReturn(Optional.of(existing));

        service.notifyCommunityLike(board, secondActor, List.of(secondActor, actor), 3);

        assertThat(existing.getTitle())
                .isEqualTo("두번째님, 좋아요맨님 외 1명(3명)이 열 글자가 넘는 게… 글을 좋아합니다.");
        assertThat(existing.isRead()).isFalse();
        verify(notificationHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("게시글 댓글은 댓글별 알림으로 저장된다")
    void notifyCommunityComment() {
        UUID commentUuid = UUID.randomUUID();

        service.notifyCommunityComment(
                board, actor, commentUuid, "댓글 내용도 열 글자를 넘습니다");

        ArgumentCaptor<NotificationHistory> captor =
                ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle())
                .isEqualTo("좋아요맨님이 열 글자가 넘는 게… 글에 “댓글 내용도 열 글…” 댓글을 달았습니다.");
        assertThat(captor.getValue().getSearchIndexId())
                .isEqualTo("COMMUNITY_COMMENT:" + commentUuid);
    }

    @Test
    @DisplayName("본인이 자기 콘텐츠에 남긴 활동은 알림을 만들지 않는다")
    void doesNotNotifySelf() {
        service.notifyCommunityLike(board, author, List.of(author), 1);

        verify(notificationHistoryRepository, never()).save(any());
        verify(notificationHistoryRepository, never())
                .findByMemberAndSearchIndexId(any(), any());
    }

    @Test
    @DisplayName("리뷰 좋아요는 인앱 알림 저장과 함께 커밋 후 푸시 이벤트를 발행한다")
    void notifyReviewLikePublishesPushEvent() {
        Review review = mock(Review.class);
        Pin pin = mock(Pin.class);
        UUID reviewUuid = UUID.randomUUID();
        given(review.getAuthor()).willReturn(author);
        given(review.getUuid()).willReturn(reviewUuid);
        given(review.getContent()).willReturn("정말 맛있는 리뷰 내용");
        given(review.getPin()).willReturn(pin);
        given(pin.getId()).willReturn(33L);
        given(notificationHistoryRepository.findByMemberAndSearchIndexId(
                author, "REVIEW_LIKE:" + reviewUuid)).willReturn(Optional.empty());

        service.notifyReviewLike(review, actor, List.of(actor), 1);

        ArgumentCaptor<ReviewLikePushRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(ReviewLikePushRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientMemberId()).isEqualTo(author.getId());
        assertThat(eventCaptor.getValue().reviewUuid()).isEqualTo(reviewUuid);
        assertThat(eventCaptor.getValue().pinId()).isEqualTo(33L);
    }

    private Member member(long id, String nickname) {
        return Member.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .email(id + "@mju.ac.kr")
                .nickname(nickname)
                .build();
    }
}
