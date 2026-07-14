package nova.mjs.domain.thingo.community.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import nova.mjs.domain.thingo.community.entity.CommunityBoard;
import nova.mjs.domain.thingo.community.entity.enumList.CommunityCategory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 커뮤니티 게시글 응답 DTO
 */
public class CommunityBoardResponse {

    /* ========================== 요약 DTO ========================== */
    @Data
    @Builder
    @AllArgsConstructor
    public static class SummaryDTO {
        private UUID uuid;
        private CommunityCategory communityCategory;
        private String title;
        private String previewContent;
        private int viewCount;
        private Boolean published;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private int likeCount;
        private int commentCount;
        private String author;
        private UUID authorUuid;
        private boolean isLiked;
        private boolean popular;

        public static SummaryDTO fromEntityPreview(CommunityBoard e,
                                                   int likeCount, int commentCount, boolean liked) {
            return SummaryDTO.builder()
                    .uuid(e.getUuid())
                    .title(e.getTitle())
                    .previewContent(e.getPreviewContent())
                    .viewCount(e.getViewCount())
                    .communityCategory(e.getCategory())
                    .published(e.getPublished())
                    .publishedAt(e.getPublishedAt())   // 그대로 반환
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .author(e.getAuthor() != null ? e.getAuthor().getNickname() : "Unknown")
                    .authorUuid(e.getAuthor() != null ? e.getAuthor().getUuid() : null)
                    .isLiked(liked)
                    .build();
        }

        public static SummaryDTO fromEntityPreview(CommunityBoard e,
                                                   int likeCount, int commentCount, boolean liked, boolean popular) {
            return SummaryDTO.builder()
                    .uuid(e.getUuid())
                    .communityCategory(e.getCategory())
                    .title(e.getTitle())
                    .previewContent(e.getPreviewContent())
                    .viewCount(e.getViewCount())
                    .published(e.getPublished())
                    .publishedAt(e.getPublishedAt())   // 그대로 반환
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .likeCount(likeCount)
                    .commentCount(commentCount)
                    .author(e.getAuthor() != null ? e.getAuthor().getNickname() : "Unknown")
                    .authorUuid(e.getAuthor() != null ? e.getAuthor().getUuid() : null)
                    .isLiked(liked)
                    .popular(popular)
                    .build();
        }
    }

/* ========================== 상세 DTO ========================== */
    @Data
    @Builder
    public static class DetailDTO {
    private UUID uuid;
    private String title;
    private CommunityCategory communityCategory;
    private String content;
    private String contentPreview;
    private int viewCount;
    private Boolean published;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int likeCount;
    private int commentCount;
    private String author;
    private UUID authorUuid;
    private boolean isLiked;
    private boolean canEdit;
    private boolean canDelete;

    public static DetailDTO fromEntity(CommunityBoard e,
                                       int likeCount, int commentCount, boolean liked, boolean canEdit, boolean canDelete) {
        return DetailDTO.builder()
                .uuid(e.getUuid())
                .communityCategory(e.getCategory())
                .title(e.getTitle())
                .content(e.getContent())
                .contentPreview(e.getPreviewContent())
                .viewCount(e.getViewCount())
                .published(e.getPublished())
                .publishedAt(e.getPublishedAt())   // atZone 제거, 그대로 사용
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .author(e.getAuthor() != null ? e.getAuthor().getNickname() : "Unknown")
                .authorUuid(e.getAuthor() != null ? e.getAuthor().getUuid() : null)
                .isLiked(liked)
                .canEdit(canEdit)
                .canDelete(canDelete)
                .build();
    }

    public static DetailDTO fromEntity(CommunityBoard e,
                                       int likeCount, int commentCount, boolean liked) {
        return DetailDTO.builder()
                .uuid(e.getUuid())
                .communityCategory(e.getCategory())
                .title(e.getTitle())
                .content(e.getContent())
                .contentPreview(e.getPreviewContent())
                .viewCount(e.getViewCount())
                .published(e.getPublished())
                .publishedAt(e.getPublishedAt())   // atZone 제거, 그대로 사용
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .author(e.getAuthor() != null ? e.getAuthor().getNickname() : "Unknown")
                .authorUuid(e.getAuthor() != null ? e.getAuthor().getUuid() : null)
                .isLiked(liked)
                .build();
        }
    }
}
