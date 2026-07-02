package nova.mjs.domain.thingo.map.support;

import org.springframework.stereotype.Component;

/**
 * 명지도 검색어 매칭/스코어링 유틸.
 *
 * 캠퍼스 규모(핀 수백 개)에서는 DB 전문검색(pg_trgm/tsvector) 없이 메모리에서 계산해도 충분하다.
 * (거리 계산을 인메모리 Haversine으로 하는 것과 동일한 판단)
 *
 * [검색 품질 전략]
 * - 정규화: 소문자화 + 공백/기호 제거로 표기 흔들림 흡수 ("투썸 플레이스" == "투썸플레이스")
 * - 다중 시그널 점수: 정확일치 > 접두 > 부분포함 > 오타유사 순, 카테고리명 일치는 보조 가중
 * - 초성 검색: 입력이 초성 자모로만 구성되면(ㅌㅆ) 이름 초성 시퀀스와 부분일치
 * - 오타 허용: 편집거리(Levenshtein) 기반 word_similarity 근사 (짧은 한글 장소명에 강함)
 */
@Component
public class MapSearchMatcher {

    /** 한글 음절의 19개 초성 (유니코드 순서) */
    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    /** 초성 자모 집합 (입력이 초성만인지 판정용) */
    private static final String LEAD_JAMO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

    private static final int HANGUL_BASE = 0xAC00;   // '가'
    private static final int HANGUL_END = 0xD7A3;    // '힣'
    private static final int SYLLABLE_BLOCK = 588;   // 중성(21) * 종성(28)

    /** 오타 허용 최소 유사도 (0~1). 1글자 오타 2글자 단어 = 0.5 통과 */
    private static final double FUZZY_THRESHOLD = 0.5;

    /**
     * 이름·카테고리명 대비 검색어 관련도 점수. 0 이하면 매칭 실패로 결과에서 제외한다.
     *
     * @param name          핀 이름 (건물명/장소명)
     * @param categoryLabel 소속 카테고리 라벨 (보조 매칭용)
     * @param query         사용자 입력 검색어
     */
    public double score(String name, String categoryLabel, String query) {
        if (query == null || query.isBlank()) {
            return 0.0;
        }

        // 1. 초성 입력이면 이름 초성 시퀀스와만 매칭 (ㅌㅆ -> 투썸플레이스)
        if (isChosungQuery(query)) {
            String chosungQuery = query.replaceAll("\\s", "");
            String nameChosung = toChosung(name);
            if (nameChosung.equals(chosungQuery)) {
                return 90.0;
            }
            if (nameChosung.startsWith(chosungQuery)) {
                return 65.0;
            }
            return nameChosung.contains(chosungQuery) ? 45.0 : 0.0;
        }

        // 2. 일반 입력: 정규화 후 다중 시그널 점수
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return 0.0;
        }
        String normalizedName = normalize(name);

        double score;
        if (normalizedName.equals(normalizedQuery)) {
            score = 100.0;                                   // 정확 일치
        } else if (normalizedName.startsWith(normalizedQuery)) {
            score = 70.0;                                    // 접두 일치
        } else if (normalizedName.contains(normalizedQuery)) {
            score = 45.0;                                    // 부분 포함
        } else {
            double fuzzy = wordSimilarity(normalizedName, normalizedQuery);
            score = fuzzy >= FUZZY_THRESHOLD ? 20.0 + fuzzy * 20.0 : 0.0; // 오타 유사 (20~40)
        }

        // 3. 카테고리명 일치는 보조 가중 (이름 매칭이 약할 때만 끌어올림)
        String normalizedCategory = normalize(categoryLabel);
        if (!normalizedCategory.isEmpty() && normalizedCategory.contains(normalizedQuery)) {
            score = Math.max(score, 25.0);
        }
        return score;
    }

    /** 소문자화 + 공백/기호 제거. 한글 음절·영문·숫자만 남긴다 */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (char c : text.toLowerCase().toCharArray()) {
            // 초성 자모(ㄱ~ㅎ) 등 낱자모는 정규화 대상에서 제외하고 음절/영숫자만 남긴다
            if (isHangulSyllable(c) || Character.isDigit(c) || (c >= 'a' && c <= 'z')) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /** 문자열의 초성 시퀀스. 한글 음절만 초성으로 변환하고 그 외 문자는 건너뛴다 */
    public String toChosung(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isHangulSyllable(c)) {
                builder.append(CHOSUNG[(c - HANGUL_BASE) / SYLLABLE_BLOCK]);
            } else if (LEAD_JAMO.indexOf(c) >= 0) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /** 입력이 초성 자모로만 구성됐는지 (초성 검색 모드 판정) */
    public boolean isChosungQuery(String query) {
        if (query == null) {
            return false;
        }
        String trimmed = query.replaceAll("\\s", "");
        if (trimmed.isEmpty()) {
            return false;
        }
        for (char c : trimmed.toCharArray()) {
            if (LEAD_JAMO.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * word_similarity 근사: name 안에서 query와 가장 잘 맞는 구간의 편집거리 유사도 최대값.
     * 긴 이름에 짧은 검색어를 맞출 때 전체 길이 차이에 유사도가 눌리지 않도록 구간별로 비교한다.
     */
    private double wordSimilarity(String name, String query) {
        if (name.isEmpty() || query.isEmpty()) {
            return 0.0;
        }
        if (name.length() <= query.length()) {
            return editSimilarity(name, query);
        }
        double best = 0.0;
        for (int start = 0; start + query.length() <= name.length(); start++) {
            best = Math.max(best, editSimilarity(name.substring(start, start + query.length()), query));
        }
        return best;
    }

    /** 편집거리 기반 유사도 (0~1) */
    private double editSimilarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 0.0 : 1.0 - (double) levenshtein(a, b) / max;
    }

    /** Levenshtein 편집거리 (두 줄 배열로 메모리 절약) */
    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private static boolean isHangulSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }
}
