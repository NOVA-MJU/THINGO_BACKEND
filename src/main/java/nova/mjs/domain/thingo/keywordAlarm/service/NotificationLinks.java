package nova.mjs.domain.thingo.keywordAlarm.service;

import java.util.Locale;

/**
 * 알림을 눌렀을 때 앱이 이동할 경로를 알림 종류별로 한 곳에서 정한다.
 *
 * 앱은 link가 http(s)면 브라우저로, '/'로 시작하면 앱 화면으로 이동하고 비어 있으면 아무 동작도 하지 않는다.
 * 학사일정·커뮤니티는 통합검색 색인에 link가 없고, 학식은 색인 문서가 아니라 link 없이 저장되던 탓에
 * 공지 외 알림은 눌러도 이동하지 않았다. 활동 알림이 저장하던 '/boards/{uuid}'는 앱에 없는 경로(404)였다.
 *
 * 경로 값은 앱(Expo Router) 라우트와 맞춘다:
 * - '/meal', '/academic-calendar': 홈 탭(모바일은 앱이 tab 파라미터로 변환)
 * - '/posts/{boardUuid}': 게시글 상세
 * - '/maps?placeId={pinId}': 명지도에서 장소 상세 시트
 */
public final class NotificationLinks {

    static final String MEAL = "/meal";
    static final String ACADEMIC_CALENDAR = "/academic-calendar";
    private static final String POSTS = "/posts/";
    private static final String LEGACY_BOARDS = "/boards/";

    private NotificationLinks() {
    }

    public static String post(Object boardUuid) {
        return POSTS + boardUuid;
    }

    public static String place(Long pinId) {
        return "/maps?placeId=" + pinId;
    }

    /**
     * @param searchIndexId 'TYPE:원본ID' 형태(키워드 알림) 또는 활동 알림 키. 커뮤니티 키워드 알림의 게시글 ID를 꺼내는 데 쓴다
     */
    public static String resolve(String type, String rawLink, String searchIndexId) {
        String normalizedType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "WEEKLY_MENU" -> MEAL;
            case "MJU_CALENDAR" -> ACADEMIC_CALENDAR;
            case "COMMUNITY", "COMMUNITY_LIKE", "COMMUNITY_COMMENT" -> communityLink(rawLink, searchIndexId);
            default -> rawLink;
        };
    }

    private static String communityLink(String rawLink, String searchIndexId) {
        if (rawLink != null && rawLink.startsWith(LEGACY_BOARDS)) {
            return POSTS + rawLink.substring(LEGACY_BOARDS.length());
        }
        if (rawLink != null && !rawLink.isBlank()) {
            return rawLink;
        }
        // 키워드 알림의 searchIndexId는 'COMMUNITY:{boardUuid}'
        if (searchIndexId != null && searchIndexId.startsWith("COMMUNITY:")) {
            return POSTS + searchIndexId.substring("COMMUNITY:".length());
        }
        return null;
    }
}
