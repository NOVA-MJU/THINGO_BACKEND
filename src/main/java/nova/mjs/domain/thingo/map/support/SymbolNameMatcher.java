package nova.mjs.domain.thingo.map.support;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 네이버 지도 기본 글자(심벌) 이름과 우리 핀 이름이 같은 곳을 가리키는지 판단한다.
 *
 * 심벌은 이름과 좌표만 주고, 표기가 우리와 조금씩 다르다("스타벅스 명지대점" / "스타벅스", "명지대학교 학생회관" / "학생회관").
 * 그래서 공백·기호·지점 꼬리를 떼고, 한쪽이 다른 쪽에 들어 있거나 두 글자 조각이 60% 이상 겹치면 같은 곳으로 본다.
 * 앱(lib/maps/match-symbol.ts)과 전수조사 스크립트(scripts/audit-places.mjs)도 같은 기준을 쓴다.
 */
public final class SymbolNameMatcher {

    private static final double MIN_BIGRAM_SIMILARITY = 0.6;

    private SymbolNameMatcher() {
    }

    public static boolean isSimilarName(String firstName, String secondName) {
        String first = normalizeName(firstName);
        String second = normalizeName(secondName);
        if (first.isEmpty() || second.isEmpty()) return false;
        if (first.equals(second)) return true;

        boolean isContained = first.contains(second) || second.contains(first);
        if (Math.min(first.length(), second.length()) >= 2 && isContained) return true;

        Set<String> firstBigrams = bigrams(first);
        Set<String> secondBigrams = bigrams(second);
        int totalCount = firstBigrams.size() + secondBigrams.size();
        if (totalCount == 0) return false;
        long sharedCount = firstBigrams.stream().filter(secondBigrams::contains).count();
        return (2.0 * sharedCount) / totalCount >= MIN_BIGRAM_SIMILARITY;
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("[^0-9a-z가-힣]", "")
                // 지점 꼬리('명지대점', '본점', '2호점')만 뗀다. '만점'처럼 이름이 '점'으로 끝나는 가게는 그대로 둔다
                .replaceAll("((명지대|명지|남가좌|북가좌|가좌|홍제|연희)점|본점|\\d+호점)$", "");
    }

    // 두 글자씩 끊은 조각 ('삼정식당' -> 삼정, 정식, 식당)
    private static Set<String> bigrams(String text) {
        return IntStream.range(0, Math.max(text.length() - 1, 0))
                .mapToObj(start -> text.substring(start, start + 2))
                .collect(Collectors.toSet());
    }
}
