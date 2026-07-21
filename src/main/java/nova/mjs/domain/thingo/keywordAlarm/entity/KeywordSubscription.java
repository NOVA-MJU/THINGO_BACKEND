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

/**
 * 키워드 알림 구독.
 *
 * 한 회원이 '알림 받을 키워드'(공백 제외 5글자 이내)와 그 키워드를 감시할
 * 카테고리(공지/학사일정/게시판, 1개 이상)를 등록한 기록.
 * (member, keyword) 조합은 유일 (같은 키워드 중복 등록 방지).
 * 신규 콘텐츠가 이 키워드/카테고리와 매칭되면 알림이 발송된다.
 */
@Entity
@Table(
        name = "keyword_subscription",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_keyword_subscription_member_keyword",
                columnNames = {"member_id", "keyword"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordSubscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_subscription_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "keyword", nullable = false, length = 5)
    private String keyword;

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
    private KeywordSubscription(Member member, String keyword, Set<AlarmCategory> categories) {
        this.member = member;
        this.keyword = keyword;
        this.categories = new LinkedHashSet<>(categories);
    }

    public static KeywordSubscription of(Member member, String keyword, Set<AlarmCategory> categories) {
        return KeywordSubscription.builder()
                .member(member)
                .keyword(keyword)
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

    /**
     * 알림 on/off 를 변경한다. 구독 자체는 유지하고 발송 대상 여부만 바꾼다.
     */
    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 이 구독이 주어진 검색 type 의 콘텐츠를 알림 대상으로 삼는지 여부.
     */
    public boolean matchesType(String searchType) {
        return categories.stream()
                .anyMatch(category -> category.getSearchType().equalsIgnoreCase(searchType));
    }
}
