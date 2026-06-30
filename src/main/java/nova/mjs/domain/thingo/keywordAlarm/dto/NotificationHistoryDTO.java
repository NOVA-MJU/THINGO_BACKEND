package nova.mjs.domain.thingo.keywordAlarm.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;

import java.time.Instant;

/**
 * 알림 내역(알림함) 응답 DTO.
 */
public class NotificationHistoryDTO {

    public static class Response {

        @Getter
        @Builder
        public static class Detail {
            private final Long id;
            private final String matchedKeyword;
            private final String type;
            private final String title;
            private final String link;
            private final boolean read;
            private final Instant sentAt;

            public static Detail from(NotificationHistory history) {
                return Detail.builder()
                        .id(history.getId())
                        .matchedKeyword(history.getMatchedKeyword())
                        .type(history.getType())
                        .title(history.getTitle())
                        .link(history.getLink())
                        .read(history.isRead())
                        .sentAt(history.getSentAt())
                        .build();
            }
        }
    }
}
