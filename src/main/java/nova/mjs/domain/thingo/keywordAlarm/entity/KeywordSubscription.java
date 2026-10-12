package nova.mjs.domain.thingo.keywordAlarm.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 키워드 알림 구독.
 *
 * 한 회원(또는 비로그인 기기)이 '알림 받을 키워드'(공백 제외 5글자 이내)와 그 키워드를 감시할
 * 카테고리(공지/학사일정/게시판, 1개 이상)를 등록한 기록.
 * 소유자는 member 또는 deviceId 중 정확히 하나(AlarmOwner, DB CHECK 제약).
 * (member, keyword) / (device, keyword) 조합은 유일 (같은 키워드 중복 등록 방지).
 * PostgreSQL 유일 제약은 NULL 을 서로 다른 값으로 보므로, 두 제약이 서로의 행에 걸리지 않는다.
 * 신규 콘텐츠가 이 키워드/카테고리와 매칭되면 알림이 발송된다.
 */
@Entity
@Table(
        name = "keyword_subscription",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_keyword_subscription_member_keyword",
                        columnNames = {"member_id", "keyword"}
                ),
                @UniqueConstraint(
                        name = "uk_keyword_subscription_device_keyword",
                        columnNames = {"device_id", "keyword"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /**
     * 비로그인 기기 소유 구독의 기기 id (device.device_id). 회원 구독이면 null.
     * 발송/조회 시 Device 엔티티를 읽을 일이 없어 연관관계 대신 값으로 둔다.
     */
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "keyword", nullable = false, length = 5)
    private String keyword;

    /**
     * 자동완성에서 선택한 표준 Topic. null이면 기존 자유 키워드 구독으로 동작한다.
     * Topic 구독은 공지 분류 결과의 계층을 사용하므로 상위 Topic 선택 시 하위 Topic까지 포함한다.
     */
    @Column(name = "topic_id", length = 64)
    private String topicId;

    /**
     * 알림 on/off. false 면 발송 경로(키워드 매칭/학식 방송)에서 제외된다(구독은 유지).
     * 기존 row 는 ddl-auto 가 default true 로 채운다.
     */
    @Column(name = "enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean enabled = true;

    /**
     * 알림을 받을 카테고리 집합(1개 이상). 값이 적고 고정적이라 별도 엔티티 대신 @ElementCollection 사용.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "keyword_subscription_category",
            joinColumns = @JoinColumn(name = "keyword_subscription_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_keyword_subscription_category",
                    columnNames = {"keyword_subscription_id", "category"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private Set<AlarmCategory> categories = new LinkedHashSet<>();

    @Builder(access = AccessLevel.PRIVATE)
    private KeywordSubscription(Member member, UUID deviceId, String keyword, String topicId,
                                Set<AlarmCategory> categories) {
        this.member = member;
        this.deviceId = deviceId;
        this.keyword = keyword;
        this.topicId = topicId;
        this.categories = new LinkedHashSet<>(categories);
    }

    public static KeywordSubscription of(Member member, String keyword, Set<AlarmCategory> categories) {
        return of(member, keyword, null, categories);
    }

    public static KeywordSubscription of(
            Member member, String keyword, String topicId, Set<AlarmCategory> categories) {
        return of(AlarmOwner.ofMember(member), keyword, topicId, categories);
    }

    public static KeywordSubscription of(
            AlarmOwner owner, String keyword, String topicId, Set<AlarmCategory> categories) {
        return KeywordSubscription.builder()
                .member(owner.member())
                .deviceId(owner.deviceId())
                .keyword(keyword)
                .topicId(topicId)
                .categories(categories)
                .build();
    }

    /**
     * 구독 카테고리를 교체한다. 상태 변경은 의도 메서드로만 허용.
     */
    public void updateCategories(Set<AlarmCategory> categories) {
        this.categories = new LinkedHashSet<>(categories);
    }

    /**
     * 감시 키워드를 교체한다. 중복(member, keyword) 검사는 호출부(서비스)가 책임진다.
     */
    public void changeKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void changeTopicId(String topicId) {
        this.topicId = topicId;
    }

    /**
     * 알림 on/off 를 변경한다. 구독 자체는 유지하고 발송 대상 여부만 바꾼다.
     */
    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AlarmOwner getOwner() {
        return new AlarmOwner(member, deviceId);
    }

    /**
     * 비로그인 기기 구독을 로그인한 회원 소유로 옮긴다(로그인 시 병합).
     */
    public void reassignTo(Member member) {
        this.member = member;
        this.deviceId = null;
    }

    /**
     * 같은 키워드의 다른 구독(로그인 전 기기 구독)을 흡수한다.
     * 카테고리는 합집합, 알림은 둘 중 하나라도 켜져 있으면 켠다(사용자가 원한 알림을 잃지 않도록).
     * 키워드 표기와 Topic 은 기존(회원) 구독 값을 유지한다.
     */
    public void absorb(KeywordSubscription other) {
        Set<AlarmCategory> merged = new LinkedHashSet<>(this.categories);
        merged.addAll(other.categories);
        this.categories = merged;
        this.enabled = this.enabled || other.enabled;
    }

    /**
     * 이 구독이 주어진 검색 type 의 콘텐츠를 알림 대상으로 삼는지 여부.
     */
    public boolean matchesType(String searchType) {
        return categories.stream()
                .anyMatch(category -> category.getSearchType().equalsIgnoreCase(searchType));
    }
}
