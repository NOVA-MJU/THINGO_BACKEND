package nova.mjs.config.elasticsearch;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.Token;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class KomoranTokenizerUtil {

    private static final Komoran komoran;

    static {
        komoran = new Komoran(DEFAULT_MODEL.FULL);
        String dicPath = Objects.requireNonNull(
                        KomoranTokenizerUtil.class.getClassLoader()
                                .getResource("komoran_user_dic.txt"))
                .getFile();
        komoran.setUserDic(dicPath);
    }

    private static final Set<String> STOPWORDS = Set.of(
            "은", "는", "에서", "으로", "하고", "이다", "하는", "을", "를", "의", "이", "가", "과", "와", "로", "하다"
    );

    private static final Pattern MEANINGFUL_CHAR = Pattern.compile("[가-힣A-Za-z0-9]");

    private KomoranTokenizerUtil() {
    }

    public static List<String> generateSuggestions(String text) {
        List<String> units = extractSuggestionUnits(text);
        if (units.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>(units);
        result.addAll(buildAdjacentNgrams(units));

        return result.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public static String buildSearchTokens(String... texts) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }

            String normalized = normalizeSearchText(text);
            String compact = compact(text);

            if (!normalized.isBlank()) {
                tokens.add(normalized);
            }
            if (!compact.isBlank()) {
                tokens.add(compact);
            }

            List<String> units = extractSuggestionUnits(text);
            tokens.addAll(units);
            tokens.addAll(buildAdjacentNgrams(units));
            tokens.addAll(buildSkippedCompounds(units));
        }

        return String.join(" ", tokens);
    }

    public static String normalizeSearchText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    public static String compact(String text) {
        return normalizeSearchText(text).replace(" ", "");
    }

    /**
     * 검색어를 PostgreSQL to_tsquery('simple', ...) 입력 문자열로 변환한다.
     *
     * 색인(search_tokens)은 Komoran 형태소 기반인데 기존 구현은 원문 keyword 를 그대로
     * plainto_tsquery 에 넣어(AND) 복합어/다어절/자연어의 recall 이 무너졌다.
     * 이를 바로잡기 위해:
     *  1. 검색어를 Komoran 으로 분석해 개별 명사/외래어 형태소를 추출 (복합어 분해)
     *  2. 공백 제거 compact 형과 공백 단위 토큰도 추가 (정확 복합 매칭 대비)
     *  3. 각 항을 정제(영숫자/한글만)하여 OR(`|`)로 결합 -> 부분 일치 허용, 랭킹으로 정밀도 확보
     *
     * 매칭 토큰이 없으면 빈 문자열을 반환한다(호출부에서 tsvector 매칭을 건너뜀).
     */
    public static String buildTsQuery(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();

        for (Token token : komoran.analyze(text).getTokenList()) {
            String pos = token.getPos();
            String morph = sanitizeLexeme(token.getMorph());
            if (morph.isBlank() || STOPWORDS.contains(morph)) {
                continue;
            }
            boolean isContent = pos.startsWith("NN") || pos.equals("SL") || pos.equals("SH") || pos.equals("SN");
            int minLen = pos.equals("SL") ? 1 : 2;
            if (isContent && morph.length() >= minLen && isMeaningful(morph)) {
                terms.add(morph);
            }
        }

        // compact(공백제거) + 공백 단위 토큰 추가: 띄어쓰기 변형/Komoran 미태깅 단어("전과" 등) 보완.
        // isMeaningful 로 완성형 음절/영숫자가 있는 것만 허용 -> 초성/자모 노이즈("ㅁㅈㄷ")는 탈락.
        String compact = sanitizeLexeme(compact(text));
        if (compact.length() >= 2 && isMeaningful(compact)) {
            terms.add(compact);
        }
        for (String word : normalizeSearchText(text).split("\\s+")) {
            String sanitized = sanitizeLexeme(word);
            if (sanitized.length() >= 2 && isMeaningful(sanitized)) {
                terms.add(sanitized);
            }
        }

        // 의미 있는 토큰이 하나도 없으면(자모/노이즈) 빈 문자열 -> tsvector 매칭 건너뜀 -> zero-result.
        return String.join(" | ", terms);
    }

    /**
     * 검색어의 내용 형태소를 AND(`&`)로 결합한 tsquery 문자열을 만든다.
     *
     * buildTsQuery(OR) 가 recall 을 담당한다면, 이 AND 쿼리는 "모든 토큰을 포함하는 문서"를
     * 가려내 랭킹 가산점(coverage boost)에 쓰인다.
     * 예: "기숙사 신청" -> "기숙사 & 신청" -> 기숙사+신청 모두 포함한 문서가 신청만 포함한 문서보다 상위.
     * 형태소가 하나뿐이면 단일 항(= 그 토큰)을, 없으면 빈 문자열을 반환한다.
     */
    public static String buildTsQueryAnd(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Token token : komoran.analyze(text).getTokenList()) {
            String pos = token.getPos();
            String morph = sanitizeLexeme(token.getMorph());
            if (morph.isBlank() || STOPWORDS.contains(morph)) {
                continue;
            }
            boolean isContent = pos.startsWith("NN") || pos.equals("SL") || pos.equals("SH") || pos.equals("SN");
            int minLen = pos.equals("SL") ? 1 : 2;
            if (isContent && morph.length() >= minLen && isMeaningful(morph)) {
                terms.add(morph);
            }
        }
        return String.join(" & ", terms);
    }

    /**
     * tsquery lexeme 안전화: 소문자화 + 영숫자/한글 외 문자 제거.
     * to_tsquery 파싱 시 연산자/특수문자로 오인되는 것을 방지한다.
     */
    private static String sanitizeLexeme(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /**
     * 완성형 한글 음절(가-힣) 또는 영숫자를 하나라도 포함하는지.
     * 한글 호환 자모(ㄱ-ㅎ, ㅏ-ㅣ)만으로 이뤄진 초성/노이즈 입력을 걸러낸다.
     */
    private static boolean isMeaningful(String value) {
        return MEANINGFUL_CHAR.matcher(value).find();
    }

    public static List<String> extractQueryTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = normalizeSearchText(text);
        String compact = compact(text);

        if (!compact.isBlank()) {
            terms.add(compact);
        }

        for (String token : normalized.split("\\s+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2) {
                terms.add(trimmed);
            }
        }

        terms.addAll(extractSuggestionUnits(text));

        return terms.stream()
                .map(String::trim)
                .filter(value -> value.length() >= 2)
                .distinct()
                .toList();
    }

    private static List<String> extractSuggestionUnits(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Token> tokens = komoran.analyze(text).getTokenList();
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (Token token : tokens) {
            String morph = token.getMorph();
            String pos = token.getPos();

            if (isYearOrOrdinal(morph)) {
                units.add(morph);
                continue;
            }

            if ((pos.startsWith("NN") || pos.equals("SL"))
                    && morph.length() >= 2
                    && !STOPWORDS.contains(morph)) {
                current.append(morph);
            } else if (current.length() > 0) {
                units.add(current.toString());
                current.setLength(0);
            }
        }

        if (current.length() > 0) {
            units.add(current.toString());
        }

        return units.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static List<String> buildAdjacentNgrams(List<String> units) {
        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i < units.size() - 1; i++) {
            String first = units.get(i);
            String second = units.get(i + 1);
            ngrams.add(first + " " + second);
            ngrams.add(first + second);
        }
        return ngrams;
    }

    private static List<String> buildSkippedCompounds(List<String> units) {
        if (units.size() < 3) {
            return List.of();
        }

        List<String> compounds = new ArrayList<>();
        int limit = Math.min(units.size(), 6);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 2; j < limit; j++) {
                compounds.add(units.get(i) + units.get(j));
                compounds.add(units.get(i) + " " + units.get(j));
            }
        }
        return compounds;
    }

    private static boolean isYearOrOrdinal(String token) {
        return token.matches("^\\d{4}년$") || token.matches("^제\\d+차$");
    }
}
