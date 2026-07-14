package nova.mjs.util.profanity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 비속어 마스킹 필터 (L1).
 *
 * 동작
 * - 사전에 등록된 단어를 문자열에서 찾아 '*' 로 치환한다(길이 보존).
 * - 문자 사이에 끼워 넣은 공백/기호 우회("시 발", "시.발")도 탐지한다.
 *   각 사전 단어를 "글자 + [공백·기호]* + 글자" 형태의 정규식으로 컴파일하기 때문이다.
 * - 초성/자모분리("ㅅㅂ", "시1발") 우회는 오탐 위험이 커 v1 범위에서 제외한다.
 *
 * 한계
 * - 사전 기반이라 명백한 욕설만 거른다. 의미 기반(19금 등)은 신고/운영(L2)이 담당한다.
 */
@Component
public class ProfanityFilter {

    // 사전 단어의 각 글자 사이에 허용할 우회 문자(공백/문장부호)
    private static final String SEPARATOR = "[\\s\\p{Punct}]*";

    // 등록된 사전 단어가 없으면 null (필터 미동작)
    private final Pattern pattern;

    @Autowired
    public ProfanityFilter(ProfanityDictionary dictionary) {
        this(dictionary.words());
    }

    // 테스트/직접 생성용 (사전 단어 주입)
    ProfanityFilter(Collection<String> words) {
        this.pattern = compile(words);
    }

    /**
     * 입력 문자열에서 비속어를 찾아 '*' 로 마스킹한 결과를 반환한다.
     * - null/공백 문자열, 사전이 비어 있으면 원문을 그대로 반환한다.
     * - 매칭 구간은 길이를 보존하여 문자 수만큼 '*' 로 바꾼다.
     */
    public String mask(String text) {
        if (text == null || text.isBlank() || pattern == null) {
            return text;
        }

        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        while (matcher.find()) {
            // 매칭 구간(구분자 포함) 길이만큼 '*' 로 치환하여 원문 길이를 유지한다
            String masked = "*".repeat(matcher.group().length());
            matcher.appendReplacement(result, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 사전 단어들을 하나의 대안(alternation) 정규식으로 컴파일한다.
     * - 긴 단어를 먼저 매칭하도록 길이 내림차순 정렬한다(부분 겹침 시 더 긴 욕설 우선).
     * - 대소문자 무시(영어 욕설 대응).
     */
    private static Pattern compile(Collection<String> words) {
        List<String> normalized = words.stream()
                .filter(word -> word != null && !word.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        if (normalized.isEmpty()) {
            return null;
        }

        String regex = normalized.stream()
                .map(ProfanityFilter::toEvasionRegex)
                .collect(Collectors.joining("|"));

        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * 단어를 "글자 사이 구분자 삽입"까지 허용하는 정규식으로 변환한다.
     * 예: "시발" → \Q시\E[\s\p{Punct}]*\Q발\E
     */
    private static String toEvasionRegex(String word) {
        return word.chars()
                .mapToObj(codePoint -> Pattern.quote(String.valueOf((char) codePoint)))
                .collect(Collectors.joining(SEPARATOR));
    }
}
