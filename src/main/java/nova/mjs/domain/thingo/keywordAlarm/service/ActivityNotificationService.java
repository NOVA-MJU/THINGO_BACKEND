package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.event.ReviewLikePushRequestedEvent;
import nova.mjs.domain.thingo.keywordAlarm.repository.NotificationHistoryRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.review.entity.Review;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자가 만든 콘텐츠에 발생한 활동을 알림함에 적재한다.
 * 푸시 전송과 분리된 인앱 알림이며, 좋아요는 대상별 한 건으로 집계한다.
 */
@Service
@RequiredArgsConstructor
public class ActivityNotificationService {

    private static final String COMMUNITY_LIKE = "COMMUNITY_LIKE";
    private static final String COMMUNITY_COMMENT = "COMMUNITY_COMMENT";
    private static final String REVIEW_LIKE = "REVIEW_LIKE";

    private final NotificationHistoryRepository notificationHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void notifyCommunityLike(CommunityBoard board, Member actor,
                                    List<Member> latestActors, int totalLikeCount) {
        if (isSelf(board.getAuthor(), actor)) {
            return;
        }

        String target = preview(board.getTitle(), 10);
        String title = likeTitle(latestActors, totalLikeCount, target, "글을");
        upsertAggregate(
                board.getAuthor(),
                COMMUNITY_LIKE + ":" + board.getUuid(),
                title,
                NotificationLinks.post(board.getUuid()),
                COMMUNITY_LIKE
        );
    }

    @Transactional
    public void notifyCommunityComment(CommunityBoard board, Member actor,
                                       UUID commentUuid, String commentContent) {
        if (isSelf(board.getAuthor(), actor)) {
            return;
        }

        String title = displayName(actor) + "님이 "
                + preview(board.getTitle(), 10) + " 글에 “"
                + preview(commentContent, 10) + "” 댓글을 달았습니다.";
        notificationHistoryRepository.save(NotificationHistory.ofActivity(
                board.getAuthor(),
                COMMUNITY_COMMENT + ":" + commentUuid,
                title,
                NotificationLinks.post(board.getUuid()),
                COMMUNITY_COMMENT
        ));
    }

    @Transactional
    public void notifyReviewLike(Review review, Member actor,
                                 List<Member> latestActors, int totalLikeCount) {
        if (isSelf(review.getAuthor(), actor)) {
            return;
        }

        String title = likeTitle(
                latestActors,
                totalLikeCount,
                preview(review.getContent(), 10),
                "리뷰를"
        );
        String link = NotificationLinks.place(review.getPin().getId());
        upsertAggregate(
                review.getAuthor(),
                REVIEW_LIKE + ":" + review.getUuid(),
                title,
                link,
                REVIEW_LIKE
        );
        eventPublisher.publishEvent(new ReviewLikePushRequestedEvent(
                review.getAuthor().getId(),
                review.getUuid(),
                review.getPin().getId(),
                title,
                link));
    }

    private void upsertAggregate(Member recipient, String key, String title,
                                 String link, String type) {
        notificationHistoryRepository.findByMemberAndSearchIndexId(recipient, key)
                .ifPresentOrElse(
                        notification -> notification.refresh(title, link),
                        () -> notificationHistoryRepository.save(
                                NotificationHistory.ofActivity(recipient, key, title, link, type))
                );
    }

    private String likeTitle(List<Member> latestActors, int totalLikeCount,
                             String targetPreview, String targetPhrase) {
        String first = latestActors.isEmpty() ? "누군가" : displayName(latestActors.get(0));
        if (totalLikeCount <= 1 || latestActors.size() == 1) {
            return first + "님이 " + targetPreview + " " + targetPhrase + " 좋아합니다.";
        }

        String second = displayName(latestActors.get(1));
        int others = Math.max(totalLikeCount - 2, 0);
        return first + "님, " + second + "님 외 " + others + "명("
                + totalLikeCount + "명)이 " + targetPreview + " " + targetPhrase + " 좋아합니다.";
    }

    private boolean isSelf(Member recipient, Member actor) {
        return recipient == null || actor == null || Objects.equals(recipient.getId(), actor.getId());
    }

    private String displayName(Member member) {
        if (member.getNickname() != null && !member.getNickname().isBlank()) {
            return member.getNickname();
        }
        return member.getName() == null || member.getName().isBlank() ? "사용자" : member.getName();
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
