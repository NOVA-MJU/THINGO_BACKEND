package nova.mjs.domain.thingo.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 명지도 장소 리뷰. 특정 장소(Pin, type=PLACE)에 대한 명지대생의 후기.
 *
 * 구성: 키워드 태그(1~5개) + 본문 평문(최대 400자) + 사진/영상(0~10개).
 * - 별점은 없다(키워드 기반).
 * - 좋아요 수(likeCount)는 게시판과 동일하게 비정규화 컬럼으로 두고,
 *   리포지토리 원자적 증감 쿼리로만 변경한다(@Setter 금지).
 * - 소프트 삭제 없음(하드 삭제 + S3 미디어 폴더 정리는 서비스가 담당).
 *
 * [uuid]
 * 공개 식별자. 상세 조회·신고(targetType=REVIEW)·차단 판정의 안정적 키.
 * 내부 id는 노출하지 않는다.
 */
@Entity
@Table(
        name = "map_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_review_uuid",
                columnNames = "uuid"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_review_id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, updatable = false)
    private UUID uuid;

    /** 리뷰 대상 장소 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_pin_id", nullable = false)
    private Pin pin;

    /** 작성자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    /** 본문 평문 (HTML 아님), 최대 400자 */
    @Column(name = "content", length = 400, nullable = false)
    private String content;

    /** 비정규화 좋아요 수. 리포지토리 원자적 증감으로만 변경 */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /**
     * 신고 누적으로 인한 자동 숨김 여부 (L2).
     * - true면 목록/상세/미디어 등 모든 조회에서 제외한다(뷰어 무관 전역 숨김, 차단과 다름).
     * - 운영자만 별도 API로 확인/복원한다.
     */
    @ColumnDefault("false")
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    /** 선택 키워드 1~5개 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "map_review_keyword",
            joinColumns = @JoinColumn(name = "map_review_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "keyword", nullable = false)
    private Set<ReviewKeyword> keywords = new HashSet<>();

    /** 사진/영상 0~10개 (표시 순서 sortOrder 오름차순) */
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReviewMedia> media = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Review(UUID uuid, Pin pin, Member author, String content, int likeCount) {
        this.uuid = uuid;
        this.pin = pin;
        this.author = author;
        this.content = content;
        this.likeCount = likeCount;
    }

    /**
     * 리뷰 생성. uuid 발급, 좋아요 0으로 시작, 키워드 집합 부착.
     * 미디어는 이후 addMedia(...)로 순서대로 붙인다.
     */
    public static Review create(Pin pin, Member author, String content, Set<ReviewKeyword> keywords) {
        Review review = Review.builder()
                .uuid(UUID.randomUUID())
                .pin(pin)
                .author(author)
                .content(content)
                .likeCount(0)
                .build();
        review.keywords.addAll(keywords);
        return review;
    }

    /** 미디어 1건을 현재 크기를 순서로 하여 부착한다(호출 순서가 곧 표시 순서). */
    public void addMedia(String url, ReviewMediaType mediaType) {
        this.media.add(ReviewMedia.of(this, url, mediaType, this.media.size()));
    }

    /** 삭제 권한 판정용: 작성자 본인인지 여부 */
    public boolean isAuthoredBy(Member member) {
        return this.author != null && this.author.equals(member);
    }

    /** 신고 임계 초과로 자동 숨김 처리 (L2). 멱등. */
    public void hideByReport() {
        this.hidden = true;
    }

    /** 운영자 검토 후 숨김 해제 (L2). */
    public void restore() {
        this.hidden = false;
    }

    /** 현재 숨김 상태 여부 */
    public boolean isHidden() {
        return this.hidden;
    }
}
