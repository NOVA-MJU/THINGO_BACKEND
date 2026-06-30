package nova.mjs.domain.thingo.keywordAlarm.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

import java.time.Instant;

/**
 * 키워드 알림 발송 내역(알림함).
 *
 * 어떤 키워드가 어떤 콘텐츠(통합검색 인덱스 id)와 매칭돼 누구에게 발송됐는지의 기록.
 * (member_id, search_index_id) 유일 제약으로 같은 회원에게 같은 콘텐츠가 두 번 알림되지
 * 않도록 막는다(권위 dedup) - 한 글이 한 사람의 여러 키워드에 걸려도 알림은 1건.
 * 대표 키워드(matchedKeyword)/구독(keywordSubscriptionId)만 남기며, 구독이 삭제돼도
 * 내역이 보존되도록 구독은 FK 가 아닌 식별자(Long)로 보관하고 표시 값은 스냅샷으로 둔다.
 */
@Entity
@Table(
        name = "notification_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_history_member_index",
                columnNames = {"member_id", "search_index_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 매칭된 구독 id (FK 아님 - 구독 삭제와 무관하게 내역 보존) */
    @Column(name = "keyword_subscription_id", nullable = false)
    private Long keywordSubscriptionId;

    @Column(name = "matched_keyword", nullable = false, length = 5)
    private String matchedKeyword;

    /** 매칭된 통합검색 인덱스 id ({TYPE}:{ORIGINAL_ID}) */
    @Column(name = "search_index_id", nullable = false, length = 64)
    private String searchIndexId;

    /** 표시용 스냅샷 (원본 변경과 무관하게 알림 시점 값 보존) */
    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "link", columnDefinition = "TEXT")
    private String link;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Builder(access = AccessLevel.PRIVATE)
    private NotificationHistory(Member member, Long keywordSubscriptionId, String matchedKeyword,
                               String searchIndexId, String title, String link, String type) {
        this.member = member;
        this.keywordSubscriptionId = keywordSubscriptionId;
        this.matchedKeyword = matchedKeyword;
        this.searchIndexId = searchIndexId;
        this.title = title;
        this.link = link;
        this.type = type;
        this.read = false;
        this.sentAt = Instant.now();
    }

    public static NotificationHistory of(Member member, Long keywordSubscriptionId, String matchedKeyword,
                                         String searchIndexId, String title, String link, String type) {
        return NotificationHistory.builder()
                .member(member)
                .keywordSubscriptionId(keywordSubscriptionId)
                .matchedKeyword(matchedKeyword)
                .searchIndexId(searchIndexId)
                .title(title)
                .link(link)
                .type(type)
                .build();
    }

    public void markAsRead() {
        this.read = true;
    }
}
