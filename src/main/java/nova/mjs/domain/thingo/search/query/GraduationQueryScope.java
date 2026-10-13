package nova.mjs.domain.thingo.search.query;

import nova.mjs.domain.thingo.semantic.SemanticTextNormalizer;

import java.util.Comparator;
import java.util.List;
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
