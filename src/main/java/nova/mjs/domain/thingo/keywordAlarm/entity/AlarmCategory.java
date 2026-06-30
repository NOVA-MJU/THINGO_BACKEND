package nova.mjs.domain.thingo.keywordAlarm.entity;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 키워드 알림 카테고리.
 *
 * 화면 설계서(06-2-3) 카테고리 칩 ↔ 콘텐츠 매핑.
 * - NOTICE/MJU_CALENDAR/COMMUNITY: 통합검색 type 과 1:1, 키워드 매칭 방식.
 * - CAFETERIA(학식): 검색 인덱스에 없는 방송형. 키워드와 무관하게 새 학식이 올라오면 구독자 전원 알림.
 *   (searchType "WEEKLY_MENU" 는 unified_search_index 에 존재하지 않으므로 키워드 매칭에는 절대 걸리지 않는다)
 */
@Getter
public enum AlarmCategory {

    NOTICE("공지사항", "NOTICE"),
    MJU_CALENDAR("학사일정", "MJU_CALENDAR"),
    COMMUNITY("게시판", "COMMUNITY"),
    CAFETERIA("학식", "WEEKLY_MENU");

    /** 화면 노출용 한국어 라벨 */
    private final String label;

    /** unified_search_index.type 값 */
    private final String searchType;

    AlarmCategory(String label, String searchType) {
        this.label = label;
        this.searchType = searchType;
    }

    /**
     * 통합검색 type 값으로 알림 카테고리를 역매핑한다.
     * 매핑되지 않는 type(NEWS/BROADCAST/DEPARTMENT_* 등)은 empty 를 반환해
     * 알림 매칭 대상에서 자연스럽게 제외된다.
     */
    public static Optional<AlarmCategory> fromSearchType(String searchType) {
        if (searchType == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> category.searchType.equalsIgnoreCase(searchType))
                .findFirst();
    }
}
