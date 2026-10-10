package nova.mjs.domain.thingo.keywordAlarm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLinksTest {

    @Test
    @DisplayName("알림 종류마다 앱에 실제로 있는 화면 경로로 이동한다")
    void resolvesAppRoutePerNotificationType() {
        assertThat(NotificationLinks.resolve("WEEKLY_MENU", null, "WEEKLY_MENU:f815002")).isEqualTo("/meal");
        assertThat(NotificationLinks.resolve("MJU_CALENDAR", null, "MJU_CALENDAR:1086"))
                .isEqualTo("/academic-calendar");
        assertThat(NotificationLinks.resolve("COMMUNITY", null, "COMMUNITY:cc893022-5192-4143-933d-71cbec350920"))
                .isEqualTo("/posts/cc893022-5192-4143-933d-71cbec350920");
        assertThat(NotificationLinks.place(42L)).isEqualTo("/maps?placeId=42");
    }

    @Test
    @DisplayName("이전 규칙으로 저장된 '/boards/' 링크는 게시글 상세 경로로 바꾸고, 공지 원문 링크는 그대로 둔다")
    void rewritesLegacyBoardLinkAndKeepsNoticeUrl() {
        assertThat(NotificationLinks.resolve("COMMUNITY_LIKE", "/boards/abc", "COMMUNITY_LIKE:abc"))
                .isEqualTo("/posts/abc");
        assertThat(NotificationLinks.resolve("COMMUNITY_COMMENT", "/boards/abc", "COMMUNITY_COMMENT:comment-1"))
                .isEqualTo("/posts/abc");

        String noticeUrl = "https://www.mju.ac.kr/mjukr/255/subview.do?enc=abc";
        assertThat(NotificationLinks.resolve("NOTICE", noticeUrl, "NOTICE:2987")).isEqualTo(noticeUrl);
    }
}
