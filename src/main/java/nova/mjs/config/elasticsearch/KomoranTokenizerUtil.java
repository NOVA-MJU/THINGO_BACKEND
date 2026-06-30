package nova.mjs.config.elasticsearch;

import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.Token;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /*
     * 검색어 동의어 사전 (query-side 확장 전용).
     * - 사용자가 친 단어로 다른 표기의 문서까지 잡도록 OR-tsquery 에 동의어를 추가한다.
     * - 학생 검색 로그/zero-result eval 에서 뽑은 도메인 표현만 보수적으로 등록(과확장 = 노이즈).
     * - 키/값은 sanitizeLexeme 결과(소문자, 공백/기호 제거)와 같은 형태여야 매칭된다.
     * - 양방향이 필요하면 양쪽 다 키로 등록한다.
     */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("학식", List.of("학생식당", "교내식당", "급식")),
            Map.entry("학생식당", List.of("학식", "교내식당")),
            Map.entry("도서관", List.of("열람실", "도서대출")),
            Map.entry("열람실", List.of("도서관")),
            Map.entry("축제", List.of("대동제", "축제일정")),
            Map.entry("대동제", List.of("축제")),
            Map.entry("기숙사", List.of("생활관", "기숙")),
            Map.entry("생활관", List.of("기숙사")),
            Map.entry("셔틀", List.of("셔틀버스", "통학버스")),
            Map.entry("셔틀버스", List.of("셔틀", "통학버스")),
            Map.entry("국장", List.of("국가장학금")),
            Map.entry("해외", List.of("국제", "글로벌")),
            Map.entry("국제", List.of("해외", "글로벌")),
            Map.entry("성적", List.of("학점")),
            Map.entry("동아리", List.of("소모임"))
    );

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

            // 개별 내용 형태소도 추가: extractSuggestionUnits 는 연속 명사를 붙여("해외일경험")
            // 색인하지만, 분해 형태소(해외, 일경험)가 있어야 "해외" 같은 부분 검색의 recall 이 산다.
            tokens.addAll(contentMorphemes(text));
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

        // 형태소 + compact + 공백토큰을 모은 뒤, 동의어를 query-side 로 확장한다.
        LinkedHashSet<String> terms = extractContentTerms(text);
        expandSynonyms(terms);

        // 의미 있는 토큰이 하나도 없으면(자모/노이즈) 빈 문자열 -> tsvector 매칭 건너뜀 -> zero-result.
        return String.join(" | ", terms);
    }

    /**
     * 색인 행의 title_tokens 컬럼 값 생성(검색어가 아니라 "문서 제목"의 토큰화).
     *
     * title_vector 는 과거 to_tsvector('simple', title) 로 만들어져 한글 복합어가 한 덩어리
     * lexeme 로 굳었다(예 "해외일경험"). 그래서 "해외" 검색이 제목을 못 짚어 제목 매칭 부스트가
     * 빗나갔다(전체 "해외" 제목 63건 중 부스트 가능 5건뿐). 제목도 검색어와 동일하게 Komoran 으로
     * 분해해 저장하면(해외|일경험) title_vector 가 "해외"를 단독 lexeme 로 갖게 되어 부스트가 발동한다.
     * 동의어 확장은 하지 않는다(문서 쪽 토큰은 원형 유지, 확장은 query-side 책임).
     */
    public static String buildTitleTokens(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return String.join(" ", extractContentTerms(text));
    }

    /**
     * Komoran 내용 형태소 + compact + 공백 단위 토큰을 중복 없이 추출(검색어/제목 공통).
     * compact/공백 토큰은 띄어쓰기 변형이나 Komoran 미태깅 단어("전과" 등)를 보완한다.
     * isMeaningful 로 완성형 음절/영숫자가 있는 것만 허용 -> 초성/자모 노이즈는 탈락한다.
     */
    private static LinkedHashSet<String> extractContentTerms(String text) {
        LinkedHashSet<String> terms = contentMorphemes(text);

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
        return terms;
    }

    /**
     * Komoran 분석 결과에서 내용 형태소(명사/외래어/한자/숫자)만 추출한다.
     * 복합어 분해의 핵심: "해외일경험" -> 해외, 일경험 처럼 개별 형태소로 쪼갠다.
     */
    private static LinkedHashSet<String> contentMorphemes(String text) {
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
        return terms;
    }

    /** 동의어 사전을 참고해 terms 에 동의어를 추가한다(원본 term 은 유지). */
    private static void expandSynonyms(LinkedHashSet<String> terms) {
        List<String> additions = new ArrayList<>();
        for (String term : terms) {
            List<String> syn = SYNONYMS.get(term);
            if (syn != null) {
                additions.addAll(syn);
            }
        }
        terms.addAll(additions);
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
        // coverage boost 용. 원본 검색어의 내용 형태소만 AND 결합한다(동의어/compact 미포함 = 엄격).
        return String.join(" & ", contentMorphemes(text));
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
