package nova.mjs.domain.thingo.community.comment.DTO;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import nova.mjs.domain.thingo.community.comment.entity.Comment;
import nova.mjs.domain.thingo.member.entity.Member;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponseDto {
    private UUID communityBoardUuid;       // 댓글이 작성된 게시글의 uuid
    private List<CommentSummaryDto> comments;

    @JsonPropertyOrder({
            "commentUUID",
            "content",
            "nickname",
            "profileImageUrl",
            "likeCount",
            "createdAt",
            "liked",
            "isAuthor",
            "replies"
    })
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CommentSummaryDto {
        private UUID commentUUID;
        private String content;
        private String nickname;
        private String profileImageUrl;
        private int likeCount;
        private LocalDateTime createdAt;
        private boolean isLiked;   // 현재 로그인한 사용자가 좋아요를 눌렀는가
        private boolean commentIsAuthor;  // ✅ 내가 쓴 댓글인지

        private List<CommentSummaryDto> replies;

        // === 팩토리 메서드들 ===

        public static CommentSummaryDto fromEntity(Comment comment, boolean isLiked, boolean commentIsAuthor) {
            return CommentSummaryDto.builder()
                    .commentUUID(comment.getUuid())
                    .content(comment.getContent())
                    .nickname(comment.getMember().getNickname())
                    .profileImageUrl(comment.getMember().getProfileImageUrl())
                    .likeCount(comment.getLikeCount())
                    .isLiked(isLiked)
                    .commentIsAuthor(commentIsAuthor) // ✅
                    .createdAt(comment.getCreatedAt())
                    .build();
        }

        public static CommentSummaryDto fromEntity(Comment comment, boolean commentIsAuthor) {
            return CommentSummaryDto.builder()
                    .commentUUID(comment.getUuid())
                    .content(comment.getContent())
                    .nickname(comment.getMember().getNickname())
                    .profileImageUrl(comment.getMember().getProfileImageUrl())
                    .likeCount(comment.getLikeCount())
                    .commentIsAuthor(commentIsAuthor) // ✅
                    .createdAt(comment.getCreatedAt())
                    .build();
        }

        // 부모 댓글 + 자식(depth=1)까지 변환
        public static CommentSummaryDto fromEntityWithReplies(Comment comment,
                                                              boolean isLiked,
                                                              Set<UUID> likedSet,
                                                              Member me) {
            return fromEntityWithReplies(comment, isLiked, likedSet, me, Set.of(), Set.of());
        }

        /**
         * 부모 댓글 + 자식(depth=1)까지 변환하되, 차단 사용자의 대댓글 + 내가 신고한 대댓글은 제외한다.
         *
         * @param hiddenMemberIds 차단 등으로 숨겨야 할 작성자 member id 집합
         * @param selfReportedUuids 신고자 본인이 신고한 댓글 uuid 집합(자가 숨김, L1.5)
         */
        public static CommentSummaryDto fromEntityWithReplies(Comment comment,
                                                              boolean isLiked,
                                                              Set<UUID> likedSet,
                                                              Member me,
                                                              Set<Long> hiddenMemberIds,
                                                              Set<UUID> selfReportedUuids) {
            boolean commentIsAuthor = me != null && comment.getMember().getId().equals(me.getId());

            CommentSummaryDto.CommentSummaryDtoBuilder builder = CommentSummaryDto.builder()
                    .commentUUID(comment.getUuid())
                    .content(comment.getContent())
                    .nickname(comment.getMember().getNickname())
                    .profileImageUrl(comment.getMember().getProfileImageUrl())
                    .likeCount(comment.getLikeCount())
                    .createdAt(comment.getCreatedAt())
                    .isLiked(isLiked)
                    .commentIsAuthor(commentIsAuthor); // ✅

            // 자식 댓글 변환 (depth=1) - 신고 자동 숨김(L2) + 차단 사용자 + 내가 신고한 대댓글은 숨김
            List<CommentSummaryDto> replyDtos = comment.getReplies().stream()
                    .filter(child -> !child.isHidden())
                    .filter(child -> hiddenMemberIds == null
                            || !hiddenMemberIds.contains(child.getMember().getId()))
                    .filter(child -> selfReportedUuids == null
                            || !selfReportedUuids.contains(child.getUuid()))
                    .map(child -> {
                        boolean childIsLiked = (likedSet != null && likedSet.contains(child.getUuid()));
                        boolean childIsAuthor = me != null && child.getMember().getId().equals(me.getId());
                        return fromEntityNoReplies(child, childIsLiked, childIsAuthor);
                    })
                    .toList();

            builder.replies(replyDtos);
            return builder.build();
        }

        public static CommentSummaryDto fromEntityNoReplies(Comment comment,
                                                            boolean isLiked,
                                                            boolean commentIsAuthor) {
            return CommentSummaryDto.builder()
                    .commentUUID(comment.getUuid())
                    .content(comment.getContent())
                    .nickname(comment.getMember().getNickname())
                    .profileImageUrl(comment.getMember().getProfileImageUrl())
                    .likeCount(comment.getLikeCount())
                    .createdAt(comment.getCreatedAt())
                    .isLiked(isLiked)
                    .commentIsAuthor(commentIsAuthor) // ✅
                    .replies(List.of())
                    .build();
        }
    }
}
/*
    // Entity 리스트 -> DTO 변환 (게시글의 모든 댓글)
    public static CommentResponseDto fromEntities(UUID communityBoardUuid, List<Comment> comments) {
        List<CommentSummaryDto> commentList = comments.stream()
                .map(CommentSummaryDto::fromEntity)
                .toList();

        return CommentResponseDto.builder()
                .communityBoardUuid(communityBoardUuid)
                .comments(commentList)
                .build();
    }

    //Entity 하나 -> DTO 변환 (단일 댓글 조회)
    public static CommentResponseDto fromEntity(Comment comment) {
        return CommentResponseDto.builder()
                .communityBoardUuid(comment.getCommunityBoard().getUuid())
                .comments(List.of(CommentSummaryDto.fromEntity(comment)))
                .build();
    }

    public Comment toEntity(CommunityBoard communityBoard, Member member) {
        return Comment.builder()
                .communityBoard(communityBoard)
                .member(member)
                .content(this.comments.get(0).getContent()) // 요청 받은 content 사용
                .likeCount(0) // 새 댓글은 좋아요 수 0
                .uuid(UUID.randomUUID()) // 댓글 UUID 자동 생성
                .build();
    }
*/


