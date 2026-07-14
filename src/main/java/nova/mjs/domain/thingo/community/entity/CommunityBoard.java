package nova.mjs.domain.thingo.community.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import nova.mjs.domain.thingo.ElasticSearch.EntityListner.CommunityEntityListener;
import nova.mjs.domain.thingo.community.comment.entity.Comment;
import nova.mjs.domain.thingo.community.entity.enumList.CommunityCategory;
import nova.mjs.domain.thingo.community.likes.entity.CommunityLike;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(CommunityEntityListener.class)
@Table(name = "community_board")
public class CommunityBoard extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_board_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunityCategory category;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Column(columnDefinition = "TEXT")
    private String previewContent;

    /**
     * 조회 수
     *
     * - 집계 값은 Integer 사용
     * - NULL 방지를 위해 기본값 0 보장
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer viewCount = 0;

    /**
     * 좋아요 수
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer likeCount = 0;

    /**
     * 댓글 수
     */
    @Builder.Default
    @Column(nullable = false)
    private Integer commentCount = 0;

    /**
     * 게시 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;

    /**
     * 게시 시각
     */
    @Column
    private LocalDateTime publishedAt;

    /**
     * 신고 누적으로 인한 자동 숨김 여부 (L2)
     *
     * - true 이면 목록/상세/HOT/인기글 등 모든 조회에서 제외한다.
     * - 뷰어와 무관한 전역 숨김이라 조회 쿼리 WHERE 로 거른다(차단과 다름).
     * - 운영자만 별도 API 로 확인/복원할 수 있다.
     */
    @Builder.Default
    @ColumnDefault("false")
    @Column(nullable = false)
    private Boolean hidden = false;

    @Builder.Default
    @OneToMany(mappedBy = "communityBoard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommunityLike> communityLikes = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "communityBoard", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private List<Comment> comment = new ArrayList<>();

    // =========================
    // 생성 메서드
    // =========================
    public static CommunityBoard create(
            String title,
            String content,
            String previewContent,
            CommunityCategory category,
            Boolean published,
            Member author
    ) {
        boolean isPublished = Boolean.TRUE.equals(published);

        return CommunityBoard.builder()
                .uuid(UUID.randomUUID())
                .title(title)
                .content(content)
                .previewContent(previewContent)
                .category(category)
                .author(author)
                .published(isPublished)
                .publishedAt(isPublished ? LocalDateTime.now() : null)
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .build();
    }

    // =========================
    // 수정 메서드
    // =========================
    public void update(
            String title,
            String content,
            String previewContent,
            CommunityCategory communityCategory,
            Boolean published
    ) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (previewContent != null) {
            this.previewContent = previewContent;
        }
        if (communityCategory != null) {
            this.category = communityCategory;
        }
        if (published != null) {
            updatePublishedState(published);
        }
    }

    private void updatePublishedState(boolean isPublished) {
        if (isPublished && !this.published) {
            this.publishedAt = LocalDateTime.now();
        } else if (!isPublished && this.published) {
            this.publishedAt = null;
        }
        this.published = isPublished;
    }

    public void increaseViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        this.viewCount++;
    }

    /**
     * 신고 임계 초과로 게시글을 자동 숨김 처리한다 (L2).
     */
    public void hideByReport() {
        this.hidden = true;
    }

    /**
     * 운영자 검토 후 숨김을 해제한다 (L2).
     */
    public void restore() {
        this.hidden = false;
    }

    /**
     * 현재 숨김 상태인지 여부. (null 방어)
     */
    public boolean isHidden() {
        return Boolean.TRUE.equals(this.hidden);
    }

}
