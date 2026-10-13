package nova.mjs.domain.thingo.search.query;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 학교 대표 공지 판별용 제목 핵심어.
 *
 * '[학사지원] 2026학년도 2학기 수강신청 일정 안내'처럼 말머리·학년도·학기·일반어(일정, 안내, 신청 등)를 걷어내면
 * 남는 말이 검색어와 같은 공지는 그 주제의 학교 대표 공지다. 군 KCU·봉사학점 같은 세부 대상 공지는 다른 말이 남아 구분된다.
 * SQL(제목)과 Java(검색어)가 같은 규칙으로 걷어내야 비교가 맞으므로 정규식을 한 곳에 둔다.
 */
public final class CanonicalNoticeTitle {

    // PostgreSQL regexp_replace와 Java 정규식에서 똑같이 동작하는 문법만 쓴다
    public static final String STRIP_REGEX =
            "\\[[^]]*\\]|\\d{4}\\s*학년도|\\d{4}\\s*-\\s*\\d\\s*학기|\\d\\s*학기"
                    + "|일정|안내문|안내|공지|기간|관련|신청|및|등|[^가-힣a-z0-9]";
    private static final Pattern STRIP = Pattern.compile(STRIP_REGEX);
    private static final int MIN_CORE_LENGTH = 2;

    private CanonicalNoticeTitle() {
    }

    /** 제목이나 검색어의 핵심어. 너무 짧으면(일반어뿐인 검색어 등) 빈 문자열 */
    public static String core(String text) {
        String core = STRIP.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT)).replaceAll("");
        return core.length() >= MIN_CORE_LENGTH ? core : "";
    }
}
