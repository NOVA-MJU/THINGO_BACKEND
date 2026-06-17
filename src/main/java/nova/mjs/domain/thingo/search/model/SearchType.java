package nova.mjs.domain.thingo.search.model;

import java.util.List;

/**
 * PostgreSQL 통합 검색 전용 SearchType.
 *
 * ES 패키지의 동일 enum 과 의도적으로 분리한다.
 * - ES 제거 시 PG 코드가 영향받지 않도록 한다.
 * - 값(name) 은 ES enum 과 동일하게 유지하여 DB 의 `type` 컬럼 호환을 보장한다.
 */
public enum SearchType {

    NOTICE,
    MJU_CALENDAR,
    DEPARTMENT_NOTICE,
    STUDENT_COUNCIL_NOTICE,
    DEPARTMENT_SCHEDULE,
    COMMUNITY,
    NEWS,
    BROADCAST;

    public static List<SearchType> overviewOrder() {
        return List.of(
                NOTICE,
                MJU_CALENDAR,
                DEPARTMENT_NOTICE,
                STUDENT_COUNCIL_NOTICE,
                DEPARTMENT_SCHEDULE,
                COMMUNITY,
                NEWS,
                BROADCAST
        );
    }

    /**
     * 외부 입력값(쿼리 파라미터 등) 정규화.
     * - null/blank -> null 반환
     * - 대소문자 무시
     * - 알 수 없는 값은 IllegalArgumentException
     */
    public static SearchType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SearchType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 검색 타입입니다: " + value);
        }
    }
}
