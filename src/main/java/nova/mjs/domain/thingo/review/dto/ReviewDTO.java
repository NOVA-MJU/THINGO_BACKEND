package nova.mjs.domain.thingo.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewKeyword;
import nova.mjs.domain.thingo.review.entity.ReviewKeywordGroup;
import nova.mjs.domain.thingo.review.entity.ReviewMedia;
import nova.mjs.domain.thingo.review.entity.ReviewMediaType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 명지도 리뷰 DTO. 도메인당 1클래스, Request/Response는 inner class로 통합(CLAUDE.md).
 */
public class ReviewDTO {

    // ==================== Request ====================

    public static class Request {

        /** 리뷰 작성 요청. 키워드 개수(1~5)·조합·미디어 개수(≤10)는 서비스에서 구체 에러코드로 검증 */
        @Getter
        @NoArgsConstructor
        public static class Create {

            @NotNull
            private Long pinId;

            /** 선택 키워드. 개수/조합 검증은 서비스(REVIEW_KEYWORD_*) */
            private List<ReviewKeyword> keywords;

            @NotBlank
            @Size(max = 400)
            private String content;

            /** 사진/영상. 개수 검증은 서비스(REVIEW_MEDIA_LIMIT_EXCEEDED) */
            @Valid
            private List<MediaItem> media;
        }

        /** 수정 요청. 장소는 바꿀 수 없고 본문·키워드·미디어를 통째로 바꾼다 (미디어를 빼면 지워진다) */
        @Getter
        @NoArgsConstructor
        public static class Update {

            /** 선택 키워드. 개수/조합 검증은 서비스(REVIEW_KEYWORD_*) */
            private List<ReviewKeyword> keywords;

            @NotBlank
            @Size(max = 400)
            private String content;

            /** 사진/영상. 개수 검증은 서비스(REVIEW_MEDIA_LIMIT_EXCEEDED) */
            @Valid
            private List<MediaItem> media;
        }

        /** 작성 요청에 담기는 미디어 1건(사전 업로드된 URL + 종류) */
        @Getter
        @NoArgsConstructor
        public static class MediaItem {

            @NotBlank
            private String url;

            @NotNull
            private ReviewMediaType mediaType;

            /**
             * 영상 목록/뷰어 진입 전에 표시할 정적 이미지 URL.
             * VIDEO는 필수이며 IMAGE는 생략 가능하다(응답에서는 원본 URL을 썸네일로 사용).
             */
            private String thumbnailUrl;
        }
    }

    // ==================== Response ====================

    public static class Response {

        /** 키워드 태그(표시용 code/emoji/label) */
        @Getter
        @Builder(access = AccessLevel.PRIVATE)
        public static class KeywordTag {
            private String code;
            private String emoji;
            private String label;

            public static KeywordTag from(ReviewKeyword keyword) {
                return KeywordTag.builder()
                        .code(keyword.name())
                        .emoji(keyword.getEmoji())
                        .label(keyword.getLabel())
                        .build();
            }
        }

        /** 미디어 1건 */
        @Getter
        @Builder(access = AccessLevel.PRIVATE)
        public static class MediaInfo {
            private String url;
            private String thumbnailUrl;
            private ReviewMediaType mediaType;
            private int sortOrder;

            public static MediaInfo from(ReviewMedia media) {
                return MediaInfo.builder()
                        .url(media.getUrl())
                        .thumbnailUrl(media.getThumbnailUrl())
                        .mediaType(media.getMediaType())
                        .sortOrder(media.getSortOrder())
                        .build();
            }
        }

        /** 작성자 요약(닉네임/프로필) */
        @Getter
        @Builder(access = AccessLevel.PRIVATE)
        public static class AuthorInfo {
            private UUID authorUuid;
            private String nickname;
            private String profileImageUrl;

            public static AuthorInfo from(Member member) {
                return AuthorInfo.builder()
                        .authorUuid(member.getUuid())
                        .nickname(member.getNickname())
                        .profileImageUrl(member.getProfileImageUrl())
                        .build();
            }
        }

        /** 리뷰 목록 항목 */
        @Getter
        @Builder
        public static class Summary {
            private UUID reviewUuid;
            private AuthorInfo author;
            private List<KeywordTag> keywords;
            private String content;
            private List<MediaInfo> media;
            private int likeCount;
            /** 목록·스토리에서 화면에 보인 횟수 */
            private int viewCount;
            @JsonProperty("isLiked")
            private boolean liked;
            @JsonProperty("isMine")
            private boolean mine;
            private LocalDateTime createdAt;

            public static Summary from(Review review, boolean isLiked, boolean isMine) {
                return Summary.builder()
                        .reviewUuid(review.getUuid())
                        .author(AuthorInfo.from(review.getAuthor()))
                        .keywords(toKeywordTags(review.getKeywords()))
                        .content(review.getContent())
                        .media(toMediaInfos(review.getMedia()))
                        .likeCount(review.getLikeCount())
                        .viewCount(review.getViewCount())
                        .liked(isLiked)
                        .mine(isMine)
                        .createdAt(review.getCreatedAt())
                        .build();
            }
        }

        /** 리뷰 상세(05-2-7) */
        @Getter
        @Builder
        public static class Detail {
            private UUID reviewUuid;
            private Long pinId;
            private AuthorInfo author;
            private List<KeywordTag> keywords;
            private String content;
            private List<MediaInfo> media;
            private int likeCount;
            /** 목록·스토리에서 화면에 보인 횟수 */
            private int viewCount;
            @JsonProperty("isLiked")
            private boolean liked;
            @JsonProperty("isMine")
            private boolean mine;
            private boolean canDelete;
            private LocalDateTime createdAt;

            public static Detail from(Review review, boolean isLiked, boolean isMine, boolean canDelete) {
                return Detail.builder()
                        .reviewUuid(review.getUuid())
                        .pinId(review.getPin().getId())
                        .author(AuthorInfo.from(review.getAuthor()))
                        .keywords(toKeywordTags(review.getKeywords()))
                        .content(review.getContent())
                        .media(toMediaInfos(review.getMedia()))
                        .likeCount(review.getLikeCount())
                        .viewCount(review.getViewCount())
                        .liked(isLiked)
                        .mine(isMine)
                        .canDelete(canDelete)
                        .createdAt(review.getCreatedAt())
                        .build();
            }
        }

        /**
         * 내가 쓴 리뷰(GET /reviews/me). 목록 항목에 장소 id·이름·좌표를 붙인다.
         * 앱이 지도에 "내가 리뷰한 곳" 핀을 찍고, 장소 상세에서 내 리뷰를 맨 위에 보여줄 때 쓴다
         */
        @Getter
        @Builder
        public static class MyReview {
            private Long pinId;
            private String pinName;
            private Double latitude;
            private Double longitude;
            private Summary review;

            public static MyReview from(Review review, boolean isLiked) {
                return MyReview.builder()
                        .pinId(review.getPin().getId())
                        .pinName(review.getPin().getName())
                        .latitude(review.getPin().getLatitude())
                        .longitude(review.getPin().getLongitude())
                        .review(Summary.from(review, isLiked, true))
                        .build();
            }
        }

        /** 조회수 올리기 요청(POST /reviews/views). 화면에 보인 리뷰 uuid 묶음 */
        @Getter
        @NoArgsConstructor
        public static class ViewRequest {
            @NotNull
            @Size(min = 1, max = 50)
            private List<UUID> reviewUuids;
        }

        /** 장소 상세 상단 사진·영상 스트립 항목 */
        @Getter
        @Builder(access = AccessLevel.PRIVATE)
        public static class MediaStripItem {
            private UUID reviewUuid;
            private String url;
            private String thumbnailUrl;
            private ReviewMediaType mediaType;
            private int sortOrder;

            public static MediaStripItem of(UUID reviewUuid, ReviewMedia media) {
                return MediaStripItem.builder()
                        .reviewUuid(reviewUuid)
                        .url(media.getUrl())
                        .thumbnailUrl(media.getThumbnailUrl())
                        .mediaType(media.getMediaType())
                        .sortOrder(media.getSortOrder())
                        .build();
            }
        }

        /** 좋아요 토글 결과 */
        @Getter
        @Builder
        public static class LikeResult {
            private boolean liked;
            private int likeCount;
        }

        /**
         * 무한 스크롤용 리뷰 목록 응답.
         * nextCursor는 서버만 해석하는 불투명 값이며 hasNext=false이면 null이다.
         * totalElements는 현재 사용자에게 실제로 노출되는 리뷰 수다
         * (자동 숨김, 차단 관계, 본인이 신고한 리뷰 제외).
         */
        @Getter
        @Builder
        public static class CursorPage {
            private List<Summary> content;
            private String nextCursor;
            private boolean hasNext;
            private int size;
            private long totalElements;
            private String sort;
        }

        /** 작성 화면용 키워드 카탈로그(카테고리 F&B 필터 반영) */
        @Getter
        @Builder
        public static class KeywordCatalog {
            @JsonProperty("isFnb")
            private boolean fnb;
            private List<GroupCatalog> groups;
        }

        @Getter
        @Builder
        public static class GroupCatalog {
            private String group;
            private String label;
            private List<KeywordTag> keywords;
        }

        // ===== 매핑 헬퍼 =====

        /**
         * 키워드 태그 목록. 표시 규칙상 '적절한 키워드 없음'은 렌더하지 않으므로 제외한다.
         * enum 정의 순서(ordinal)로 정렬해 표시 순서를 안정화한다.
         */
        private static List<KeywordTag> toKeywordTags(Set<ReviewKeyword> keywords) {
            return keywords.stream()
                    .filter(keyword -> !keyword.isNoneAppropriate())
                    .sorted(Comparator.comparingInt(Enum::ordinal))
                    .map(KeywordTag::from)
                    .toList();
        }

        private static List<MediaInfo> toMediaInfos(List<ReviewMedia> media) {
            return media.stream()
                    .sorted(Comparator.comparingInt(ReviewMedia::getSortOrder))
                    .map(MediaInfo::from)
                    .toList();
        }
    }

    // ==================== 정적 카탈로그 빌더 ====================

    /**
     * 키워드 카탈로그 생성. isFnb=false면 F&B 전용 키워드를 제외한다.
     * 그룹 순서는 enum 정의 순서(FOOD_PRICE→MOOD→ETC)를 따른다.
     */
    public static Response.KeywordCatalog buildKeywordCatalog(boolean isFnb) {
        List<Response.GroupCatalog> groups = Arrays.stream(ReviewKeywordGroup.values())
                .map(group -> Response.GroupCatalog.builder()
                        .group(group.name())
                        .label(group.getLabel())
                        .keywords(Arrays.stream(ReviewKeyword.values())
                                .filter(keyword -> keyword.getGroup() == group)
                                .filter(keyword -> isFnb || !keyword.isFbOnly())
                                .map(Response.KeywordTag::from)
                                .toList())
                        .build())
                .toList();

        return Response.KeywordCatalog.builder()
                .fnb(isFnb)
                .groups(groups)
                .build();
    }
}
