package nova.mjs.domain.thingo.department.entity.enumList;

import lombok.Getter;
@Getter
public enum DepartmentName {

    /* 인문대학 */
    ASIA_MIDDLE_EAST_LANGUAGES("아시아·중동어문학부", College.HUMANITIES),
    CHINESE_LITERATURE("중어중문학전공", College.HUMANITIES),
    JAPANESE_LITERATURE("일어일문학전공", College.HUMANITIES),
    ARABIC_STUDIES("아랍지역학전공", College.HUMANITIES),
    KOREAN_STUDIES("글로벌한국어학전공", College.HUMANITIES),
    CULTURAL_CONTENT_STUDIES("인문콘텐츠학부", College.HUMANITIES),
    KOREAN_LITERATURE("국어국문학전공", College.HUMANITIES),
    ENGLISH_LITERATURE("영어영문학전공", College.HUMANITIES),
    ART_HISTORY("미술사·역사학전공", College.HUMANITIES),
    ART_HISTORY_DEPARTMENT("미술사학과", College.HUMANITIES),
    HISTORY_DEPARTMENT("사학과", College.HUMANITIES),
    LIBRARY_SCIENCE("문헌정보학전공", College.HUMANITIES),
    GLOBAL_CULTURAL_CONTENT_STUDIES("글로벌문화콘텐츠학전공", College.HUMANITIES),
    CREATIVE_WRITING("문예창작학과", College.HUMANITIES),
    PHILOSOPHY("철학과", College.HUMANITIES),

    /* 사회과학대학 */
    PUBLIC_ADMINISTRATION("행정학전공", College.SOCIAL_SCIENCES),
    SCHOOL_OF_PUBLIC_SERVICE("공공인재학부", College.SOCIAL_SCIENCES),
    POLITICAL_DIPLOMACY("정치외교학전공", College.SOCIAL_SCIENCES),
    ECONOMICS_STATISTICS("경상·통계학부", College.SOCIAL_SCIENCES),
    ECONOMICS("경제학전공", College.SOCIAL_SCIENCES),
    INTERNATIONAL_TRADE("국제통상학전공", College.SOCIAL_SCIENCES),
    APPLIED_STATISTICS("응용통계학전공", College.SOCIAL_SCIENCES),
    LAW("법학과", College.SOCIAL_SCIENCES),

    /* 경영대학 */
    BUSINESS_DEPARTMENT("경영학부", College.BUSINESS),
    BUSINESS_ADMINISTRATION("경영학전공", College.BUSINESS),
    GLOBAL_BUSINESS_STUDIES("글로벌비즈니스학전공", College.BUSINESS),
    MANAGEMENT_INFORMATION_SYSTEMS("경영정보학과", College.BUSINESS),

    /* 미디어·휴먼라이프대학 */
    DIGITAL_MEDIA_STUDIES("디지털미디어학부", College.MEDIA_HUMANLIFE),
    YOUTH_GUIDANCE_CHILD_STUDIES("청소년지도·아동학부", College.MEDIA_HUMANLIFE),
    YOUTH_GUIDANCE_STUDIES("청소년지도학전공", College.MEDIA_HUMANLIFE),
    CHILD_STUDIES("아동학전공", College.MEDIA_HUMANLIFE),

    /* 인공지능·소프트웨어융합대학 */
    CONVERGENT_SOFTWARE_STUDIES("융합소프트웨어학부", College.AI_SOFTWARE),
    APPLICATION_SOFTWARE("응용소프트웨어전공", College.AI_SOFTWARE),
    DATA_SCIENCE("데이터사이언스전공", College.AI_SOFTWARE),
    AI("인공지능전공", College.AI_SOFTWARE),
    DIGITAL_CONTENT_DESIGN_STUDIES("디지털콘텐츠디자인학과", College.AI_SOFTWARE),

    /* 미래융합대학 */
    CREATIVE_CONVERGENCE_TALENT_DEPARTMENT("창의융합인재학부", College.FUTURE_CONVERGENCE),
    SOCIAL_WELFARE("사회복지학과", College.FUTURE_CONVERGENCE),
    REAL_ESTATE("부동산학과", College.FUTURE_CONVERGENCE),
    LAW_ADMINISTRATION("법무행정학과", College.FUTURE_CONVERGENCE),
    PSYCHOLOGY_THERAPY("심리치료학과", College.FUTURE_CONVERGENCE),
    FUTURE_CONVERGENCE_BUSINESS("미래융합경영학과", College.FUTURE_CONVERGENCE),
    MULTI_DESIGN("멀티디자인학과", College.FUTURE_CONVERGENCE),
    ACCOUNTING_TAXATION("회계세무학과", College.FUTURE_CONVERGENCE),
    CONTRACT("계약학과", College.FUTURE_CONVERGENCE),

    /* 아너칼리지 */
    FREE_MAJOR("자율전공학부(인문)", College.HONOR),

    /* 안전 */
    OTHER("기타", null);

    private final String label;
    private final College college;

    DepartmentName(String label, College college) {
        this.label = label;
        this.college = college;
    }

    /**
     * 구글 프로필 소속 문구("학생/융합소프트웨어학부 데이터사이언스전공")에서 학과를 찾는다. 없으면 null.
     * 뒤에 적힌 이름이 더 구체적이므로(학부 다음 전공) 가장 뒤에서 끝나는 학과를 고르고,
     * 끝 위치가 같으면 긴 이름을 골라 "미술사학과"가 "사학과"로 잡히지 않게 한다
     */
    public static DepartmentName fromAffiliation(String affiliation) {
        if (affiliation == null) {
            return null;
        }
        String target = normalizeAffiliation(affiliation);
        DepartmentName best = null;
        int bestEnd = -1;
        for (DepartmentName department : values()) {
            if (department.college == null) {
                continue;
            }
            String label = normalizeAffiliation(department.label);
            int start = target.lastIndexOf(label);
            if (start < 0) {
                continue;
            }
            int end = start + label.length();
            if (end > bestEnd || (end == bestEnd && label.length() > normalizeAffiliation(best.label).length())) {
                best = department;
                bestEnd = end;
            }
        }
        return best;
    }

    // 표기가 조금씩 달라도("자율전공학부 (인문)", "경상 통계학부") 맞도록 공백·괄호·가운뎃점을 지우고 비교한다
    private static String normalizeAffiliation(String text) {
        return text.replaceAll("[\\s()·]", "");
    }

    public static DepartmentName fromString(String value) {
        try {
            return DepartmentName.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid department name: " + value);
        }
    }
}