package nova.mjs.domain.thingo.search.indexing;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공지 본문에서 "유효 마감 시점(valid_until)"을 추정하는 추출기.
 *
 * 동작
 *  - 본문에서 연/월/일 3요소가 모두 있는 완전 날짜(예: 2026.03.15, 2026년 3월 15일, 2026-03-15)만 인식한다.
 *  - 신청기간처럼 날짜가 여러 개면 "가장 늦은 날짜"를 마감으로 본다(기간 종료 시점).
 *  - 인식 실패/모호하면 Optional.empty 를 돌려준다.
 *    → 호출부는 null(=무기한)으로 저장하므로, 잘못 추정해 결과를 망가뜨리지 않는다(안전 측 기본값).
 *
 * 주의
 *  - "2025학년도"처럼 월/일이 없는 표현은 날짜로 인식하지 않는다(연도만으론 마감 아님).
 *  - 추정이므로 100% 정확하지 않다. 정확한 마감이 필요한 도메인(학사일정/학과일정)은
 *    소스 endDate 를 직접 사용한다(본 추출기는 NOTICE 본문 보조용).
 */
@Component
public class DeadlineExtractor {

    // 연(YYYY) - 월(1~2자리) - 일(1~2자리). 구분자는 . - / 또는 한글(년/월/일).
    private static final Pattern FULL_DATE = Pattern.compile(
            "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?");

    // 비현실적 연도(전화번호 등 오인식)를 거른다.
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    /**
     * 본문에서 마감 시점을 추출한다.
     *
     * @param content 공지 본문(정규화 후)
     * @return 가장 늦은 완전 날짜의 그날 끝(23:59:59). 없으면 empty.
     */
    public Optional<Instant> extract(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        // 본문 내 모든 완전 날짜를 훑어 가장 늦은 날짜를 마감으로 채택한다.
        Matcher matcher = FULL_DATE.matcher(content);
        LocalDate latest = null;
        while (matcher.find()) {
            LocalDate parsed = toLocalDate(matcher.group(1), matcher.group(2), matcher.group(3));
            if (parsed == null) {
                continue;
            }
            if (latest == null || parsed.isAfter(latest)) {
                latest = parsed;
            }
        }

        if (latest == null) {
            return Optional.empty();
        }

        // 마감일은 "그날까지 유효"이므로 그날의 끝 시각으로 환산한다.
        Instant deadline = latest.atTime(23, 59, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return Optional.of(deadline);
    }

    /**
     * 연/월/일 문자열을 LocalDate 로 변환한다. 범위를 벗어나거나 실재하지 않는 날짜면 null.
     */
    private LocalDate toLocalDate(String year, String month, String day) {
        try {
            int y = Integer.parseInt(year);
            int mo = Integer.parseInt(month);
            int d = Integer.parseInt(day);
            if (y < MIN_YEAR || y > MAX_YEAR) {
                return null;
            }
            if (mo < 1 || mo > 12 || d < 1 || d > 31) {
                return null;
            }
            // 2026-02-30 같은 비실재 날짜는 LocalDate.of 가 예외를 던진다 → null.
            return LocalDate.of(y, mo, d);
        } catch (Exception e) {
            return null;
        }
    }
}
