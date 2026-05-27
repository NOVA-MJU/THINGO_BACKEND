package nova.mjs.domain.thingo.notice.dto;

import lombok.*;
import nova.mjs.domain.thingo.notice.entity.Notice;

import java.time.LocalDateTime;

/**
 * 공지 응답 DTO
 *
 * - LIST  : 공지 목록 조회용 (가볍고 빠름)
 * - DETAIL: 공지 상세 조회용 (본문 포함)
 *
 * 설계 원칙:
 * 1. 목록 조회 시에는 content를 포함하지 않는다.
 * 2. 상세 조회 시에만 content를 포함한다.
 * 3. DTO 책임은 "응답 형태 정의"까지만 가진다.
 */
public class NoticeResponseDto {

    /**s
     * 공지 목록 조회용 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {

        private String title;          // 공지 제목
        private LocalDateTime date;    // 공지 날짜
        private String category;       // 공지 카테고리
        private String link;           // 공지 링크
        private Integer viewCount;     // 공지 조회수

        /**
         * Entity → 목록 DTO 변환
         */
        public static Summary fromEntity(Notice notice) {
            return Summary.builder()
                    .title(notice.getTitle())
                    .date(notice.getDate())
                    .category(notice.getCategory())
                    .link(notice.getLink())
                    .viewCount(notice.getViewCount())
                    .build();
        }
    }

    /**
     * 공지 상세 조회용 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {

        private String title;          // 공지 제목
        private LocalDateTime date;    // 공지 날짜
        private String category;       // 공지 카테고리
        private String link;           // 공지 링크
        private String content;        // 공지 본문 (HTML)

        /**
         * Entity → 상세 DTO 변환
         */
        public static Detail fromEntity(Notice notice) {
            return Detail.builder()
                    .title(notice.getTitle())
                    .date(notice.getDate())
                    .category(notice.getCategory())
                    .link(notice.getLink())
                    .content(notice.getContent())
                    .build();
        }
    }
}
