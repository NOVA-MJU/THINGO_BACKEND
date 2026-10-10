package nova.mjs.domain.thingo.keywordAlarm.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.keywordAlarm.service.NotificationLinks;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;

/**
 * 알림 내역(알림함) 응답 DTO.
 */
public class NotificationHistoryDTO {

    public static class Response {

        @Getter
        @Builder
        public static class Inbox {
            private final List<Detail> content;
            private final long unreadCount;
            private final boolean hasUnread;
            private final long totalElements;
            private final int totalPages;
            private final int page;
            private final int size;
            private final boolean first;
            private final boolean last;

            public static Inbox of(Page<Detail> notifications, long unreadCount) {
                return Inbox.builder()
                        .content(notifications.getContent())
                        .unreadCount(unreadCount)
                        .hasUnread(unreadCount > 0)
                        .totalElements(notifications.getTotalElements())
                        .totalPages(notifications.getTotalPages())
                        .page(notifications.getNumber())
                        .size(notifications.getSize())
                        .first(notifications.isFirst())
                        .last(notifications.isLast())
                        .build();
            }
        }

        @Getter
        @Builder
        public static class UnreadStatus {
            private final long unreadCount;
            private final boolean hasUnread;

            public static UnreadStatus of(long unreadCount) {
                return UnreadStatus.builder()
                        .unreadCount(unreadCount)
                        .hasUnread(unreadCount > 0)
                        .build();
            }
        }

        @Getter
        @Builder
        public static class Detail {
            private final Long id;
            /** 기존 클라이언트 호환 필드. 새 화면에서는 keyword 사용을 권장한다. */
            private final String matchedKeyword;
            /** 키워드 알림에만 값이 있고, 학식 등 방송형 알림은 null이다. */
            private final String keyword;
            private final String type;
            private final String categoryCode;
            private final String category;
            private final String title;
            private final String link;
            private final boolean read;
            private final Instant sentAt;
            /** 프론트 상대/절대 시간 계산용 Unix epoch milliseconds. */
            private final long timestamp;

            public static Detail from(NotificationHistory history) {
                AlarmCategory alarmCategory = AlarmCategory.fromSearchType(history.getType())
                        .orElse(null);
                String categoryCode = categoryCode(history.getType(), alarmCategory);
                String category = categoryLabel(history.getType(), alarmCategory);
                String keyword = isKeywordNotification(history.getType())
                        ? history.getMatchedKeyword()
                        : null;

                return Detail.builder()
                        .id(history.getId())
                        .matchedKeyword(history.getMatchedKeyword())
                        .keyword(keyword)
                        .type(history.getType())
                        .categoryCode(categoryCode)
                        .category(category)
                        .title(history.getTitle())
                        // 링크 규칙이 바뀌기 전에 저장된 알림(링크 없음, '/boards/..')도 지금 규칙으로 이동하도록 응답 시 보정한다
                        .link(NotificationLinks.resolve(
                                history.getType(), history.getLink(), history.getSearchIndexId()))
                        .read(history.isRead())
                        .sentAt(history.getSentAt())
                        .timestamp(history.getSentAt().toEpochMilli())
                        .build();
            }

            private static boolean isKeywordNotification(String type) {
                return "NOTICE".equalsIgnoreCase(type)
                        || "MJU_CALENDAR".equalsIgnoreCase(type)
                        || "COMMUNITY".equalsIgnoreCase(type);
            }

            private static String categoryCode(String type, AlarmCategory alarmCategory) {
                if ("COMMUNITY_LIKE".equalsIgnoreCase(type)
                        || "COMMUNITY_COMMENT".equalsIgnoreCase(type)) {
                    return "COMMUNITY";
                }
                if ("REVIEW_LIKE".equalsIgnoreCase(type)) {
                    return "MAP";
                }
                return alarmCategory == null ? null : alarmCategory.name();
            }

            private static String categoryLabel(String type, AlarmCategory alarmCategory) {
                if ("COMMUNITY_LIKE".equalsIgnoreCase(type)
                        || "COMMUNITY_COMMENT".equalsIgnoreCase(type)) {
                    return "게시판";
                }
                if ("REVIEW_LIKE".equalsIgnoreCase(type)) {
                    return "명지도";
                }
                return alarmCategory == null ? null : alarmCategory.getLabel();
            }
        }

        @Getter
        @Builder
        public static class ReadAllResult {
            private final int updatedCount;
            private final long unreadCount;
            private final boolean hasUnread;

            public static ReadAllResult of(int updatedCount) {
                return ReadAllResult.builder()
                        .updatedCount(updatedCount)
                        .unreadCount(0)
                        .hasUnread(false)
                        .build();
            }
        }
    }
}
