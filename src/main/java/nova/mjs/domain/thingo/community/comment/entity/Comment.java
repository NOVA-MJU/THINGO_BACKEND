package nova.mjs.domain.thingo.community.comment.entity;
import lombok.*;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import nova.mjs.domain.thingo.community.comment.likes.entity.CommentLike;
import nova.mjs.util.entity.BaseEntity;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.member.entity.Member;
import static nova.mjs.domain.thingo.community.util.ContentPreviewUtil.makePreview;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "comment")
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comments_id")
    private long id; // 댓글 id

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_board_id", nullable = false)
    private CommunityBoard communityBoard ; // 댓글이 속한 게시물

    @ManyToOne
    @JoinColumn(name = "member_id", nullable = false)
    private Member member; // 작성자의 nickname, id

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // 내용

    @Column
    private int likeCount; // 좋아요 수

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")  // 부모 댓글 ID 저장
    private Comment parent;

    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CommentLike> commentLike = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String previewContent; // 댓글 미리보기

    /**
     * 신고 누적으로 인한 자동 숨김 여부 (L2)
     * - true 이면 댓글 목록 조회에서 제외한다(대댓글 포함).
     */
    @Builder.Default
    @ColumnDefault("false")
    @Column(nullable = false)
    private Boolean hidden = false;


    public static Comment create(CommunityBoard communityBoard, Member member, String content) {
        return Comment.builder()
                .uuid(UUID.randomUUID())
                .communityBoard(communityBoard)
                .member(member)
                .content(content)
                .previewContent(makePreview(content)) // 유틸 사용
                .likeCount(0)
                .build();
    }

    // 부모가 있는 댓글(대댓글) 생성
    public static Comment createReply(Comment parent, Member member, String content) {
        // 부모 댓글이 속한 CommunityBoard를 그대로 사용 (부모와 같은 게시글)
        CommunityBoard board = parent.getCommunityBoard();

        Comment reply = Comment.builder()
                .uuid(UUID.randomUUID())
                .communityBoard(board)
                .member(member)
                .content(content)
                .previewContent(makePreview(content)) // 유틸 사용
                .likeCount(0)
                .parent(parent)  // 부모 설정
                .build();

        // 부모의 replies에도 연결
        parent.getReplies().add(reply);

        return reply;
    }


    // 게시물 좋아요
    public void increaseLikeCommentCount() {
        this.likeCount++;
    }

    public void decreaseLikeCommentCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    /**
     * 신고 임계 초과로 댓글을 자동 숨김 처리한다 (L2).
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
