package nova.mjs.domain.thingo.search.query;

import nova.mjs.domain.thingo.semantic.SemanticTextNormalizer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 졸업요건 검색어가 특정 학과가 아니라 학번 공통 기준을 묻는지 판별한다.
 *
 * '2025학번 졸업학점', '졸업학점'처럼 학번과 졸업 관련 일반어만 있으면 학번 공통 기준(공통·핵심교양, 졸업 이수학점)을
 * 먼저 보여줘야 한다. 학과별 학기교 문서는 제목에 학번·졸업·학점이 모두 들어 있어, 그대로 두면 아무 학과 문서가 위를 채운다.
 * 학과·단과대 이름이 하나라도 남으면 학과별 문서를 찾는 것으로 본다.
 */
public final class GraduationQueryScope {

    private static final Pattern COHORT = Pattern.compile("\\d{2,4}학번(이후|까지)?");
    private static final Pattern COHORT_YEAR = Pattern.compile("(\\d{2,4})학번");
    // 학사안내문 졸업요건 표의 학번 구간 제목. 학기마다 학사안내문 구성이 바뀌면 함께 바꾼다
    private static final List<CohortRange> COHORT_RANGES = List.of(
            new CohortRange(2025, Integer.MAX_VALUE, "2025학번 이후"),
            new CohortRange(2018, 2024, "2018~2024학번"),
            new CohortRange(2015, 2017, "2015~2017학번"),
            new CohortRange(2009, 2014, "2009~2014학번"));

    private record CohortRange(int from, int to, String titleLabel) {
    }
    private static final List<String> GRADUATION_WORDS = List.of("졸업", "학기교", "이수학점");
    // 학번 공통 기준을 물을 때 함께 쓰는 일반어. 긴 말부터 지워야 '졸업학점'이 '졸업'+'학점' 조각으로 남지 않는다
    private static final List<String> GENERIC_WORDS = List.of(
                    "졸업이수학점", "졸업학점", "졸업요건", "졸업기준", "이수학점", "학문기초교양", "학기교",
                    "몇학점", "필요학점", "최소", "필요", "기준", "요건", "졸업", "학점", "이수", "교양", "몇", "학번",
                    "이후", "까지", "알려줘", "얼마", "총")
            .stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    private static final Pattern PARTICLES = Pattern.compile("[은는이가의을를요도]");

    private GraduationQueryScope() {
    }

    /**
     * 졸업요건 검색어의 학번('21학번', '2021학번')이 속한 학사안내문 학번 구간 제목('2018~2024학번').
     * 두 자리 학번은 50 이상이면 1900년대로 본다. 졸업요건 검색이 아니거나 구간 밖이면 empty.
     */
    public static Optional<String> cohortTitleLabel(String keyword) {
        String key = SemanticTextNormalizer.lookupKey(keyword == null ? "" : keyword);
        if (GRADUATION_WORDS.stream().noneMatch(key::contains)) {
            return Optional.empty();
        }
        Matcher matcher = COHORT_YEAR.matcher(key);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        if (year < 100) {
            year += year >= 50 ? 1900 : 2000;
        }
        int admissionYear = year;
        return COHORT_RANGES.stream()
                .filter(range -> admissionYear >= range.from() && admissionYear <= range.to())
                .map(CohortRange::titleLabel)
                .findFirst();
    }

    /**
     * 졸업요건 검색어에서 학번·졸업 일반어를 뺀 나머지(학과·단과대 이름 등). '경영학과 21학번 졸업학점' → '경영학과'.
     * 졸업요건 검색이 아니거나 남는 말이 없으면 빈 문자열.
     */
    public static String subjectText(String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        String key = SemanticTextNormalizer.lookupKey(text);
        if (GRADUATION_WORDS.stream().noneMatch(key::contains)) {
            return "";
        }
        List<String> subjects = new java.util.ArrayList<>();
        for (String token : text.split("\\s+")) {
            String remainder = COHORT.matcher(SemanticTextNormalizer.lookupKey(token)).replaceAll("");
            for (String word : GENERIC_WORDS) {
                remainder = remainder.replace(word, "");
            }
            if (!PARTICLES.matcher(remainder).replaceAll("").isBlank()) {
                subjects.add(remainder);
            }
        }
        return String.join(" ", subjects);
    }

    public static boolean isCohortWide(String keyword) {
        String key = SemanticTextNormalizer.lookupKey(keyword == null ? "" : keyword);
        if (GRADUATION_WORDS.stream().noneMatch(key::contains)) {
            return false;
        }
        String remainder = COHORT.matcher(key).replaceAll("");
        for (String word : GENERIC_WORDS) {
            remainder = remainder.replace(word, "");
        }
        return PARTICLES.matcher(remainder).replaceAll("").isBlank();
    }
}
