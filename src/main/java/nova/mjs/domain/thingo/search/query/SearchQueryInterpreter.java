package nova.mjs.domain.thingo.search.query;

import lombok.RequiredArgsConstructor;
import nova.mjs.config.elasticsearch.KomoranTokenizerUtil;
import nova.mjs.domain.thingo.semantic.ResolvedTopic;
import nova.mjs.domain.thingo.semantic.SemanticTextNormalizer;
import nova.mjs.domain.thingo.semantic.TopicCatalog;
import nova.mjs.domain.thingo.semantic.NoticeEventType;
import nova.mjs.domain.thingo.semantic.SearchIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 원문 검색어를 핵심 개념 중심의 PostgreSQL tsquery 계획으로 변환한다. */
@Component
@RequiredArgsConstructor
public class SearchQueryInterpreter {

    private static final Set<String> QUESTION_TERMS = Set.of(
            "언제", "언제까지", "언제까지야", "어디", "어디서", "뭐야", "무엇", "알려줘", "알려"
    );

    private final TopicCatalog topicCatalog;

    public SearchQueryPlan interpret(String rawQuery) {
        String original = rawQuery == null ? "" : rawQuery.trim();
        if (original.isBlank()) {
            return new SearchQueryPlan(original, "", "", "", List.of(),
                    NoticeEventType.UNKNOWN, SearchIntent.GENERAL);
        }

        NoticeEventType eventType = detectEventType(original);
        SearchIntent searchIntent = detectSearchIntent(original);

        String defaultOr = KomoranTokenizerUtil.buildTsQuery(original);
        String defaultAnd = KomoranTokenizerUtil.buildTsQueryAnd(original);
        // 검색어에 먼저 나온 개념을 앞에 둔다. 점수식이 첫 개념에 가장 큰 가산점을 주기 때문이다
        String originalKey = SemanticTextNormalizer.lookupKey(original);
        List<ResolvedTopic> topics = topicCatalog.resolve(original).stream()
                .filter(match -> match.topic().searchable())
                .sorted(Comparator.comparingInt(match -> aliasPosition(originalKey, match.matchedAlias())))
                .toList();
        topics = resolveBroadGlobalProgram(original, topics);

        if (topics.isEmpty()) {
            return new SearchQueryPlan(original, defaultOr, defaultOr, defaultAnd, List.of(),
                    eventType, searchIntent);
        }

        List<String> conceptGroups = new ArrayList<>();
        LinkedHashSet<String> allAnchors = new LinkedHashSet<>();
        LinkedHashSet<String> aliasKeys = new LinkedHashSet<>();

        for (ResolvedTopic resolved : topics) {
            LinkedHashSet<String> anchors = sanitizeTerms(resolved.topic().searchTerms());
            if (!anchors.isEmpty()) {
                allAnchors.addAll(anchors);
                conceptGroups.add(parenthesizeOr(anchors));
            }
            aliasKeys.add(SemanticTextNormalizer.lookupKey(resolved.matchedAlias()));
        }

        if (conceptGroups.isEmpty()) {
            return new SearchQueryPlan(original, defaultOr, defaultOr, defaultAnd, List.of(),
                    eventType, searchIntent);
        }

        LinkedHashSet<String> modifiers = extractModifiers(defaultOr, allAnchors, aliasKeys);
        String matchQuery = String.join(" & ", conceptGroups);

        LinkedHashSet<String> rankTerms = new LinkedHashSet<>(allAnchors);
        rankTerms.addAll(modifiers);
        String rankQuery = String.join(" | ", rankTerms);

        String coverageQuery = modifiers.isEmpty()
                ? matchQuery
                : matchQuery + " & " + parenthesizeOr(modifiers);

        return new SearchQueryPlan(
                original,
                matchQuery,
                rankQuery,
                coverageQuery,
                topics.stream().map(match -> match.topic().topicId()).toList(),
                eventType,
                searchIntent
        );
    }

    /**
     * "해외"는 공고 분류 alias로 사용하면 일반 해외 기사까지 과분류될 수 있으므로
     * 검색 질의에서만 상위 GLOBAL_PROGRAM 토픽으로 해석한다. 구체 토픽이 이미
     * 해석된 경우에는 해외인턴, 교환학생, 해외봉사 등의 세부 토픽을 유지한다.
     */
    private List<ResolvedTopic> resolveBroadGlobalProgram(String original,
                                                          List<ResolvedTopic> resolvedTopics) {
        if (!resolvedTopics.isEmpty()) {
            return resolvedTopics;
        }

        String key = SemanticTextNormalizer.lookupKey(original);
        if (!key.contains("해외")) {
            return resolvedTopics;
        }

        return topicCatalog.find("GLOBAL_PROGRAM")
                .filter(topic -> topic.enabled() && topic.searchable())
                .map(topic -> List.of(new ResolvedTopic(topic, "해외", 60)))
                .orElse(resolvedTopics);
    }

    private SearchIntent detectSearchIntent(String query) {
        String key = SemanticTextNormalizer.lookupKey(query);
        if (containsAny(key, "언제", "기간", "마감", "기한", "까지")) return SearchIntent.APPLICATION_PERIOD;
        if (containsAny(key, "신청방법", "어떻게", "방법")) return SearchIntent.APPLICATION_METHOD;
        if (containsAny(key, "결과", "합격", "선발여부")) return SearchIntent.RESULT_LOOKUP;
        if (containsAny(key, "대상", "자격", "조건", "누가")) return SearchIntent.ELIGIBILITY;
        return SearchIntent.GENERAL;
    }

    private NoticeEventType detectEventType(String query) {
        String key = SemanticTextNormalizer.lookupKey(query);
        if (containsAny(key, "취소")) return NoticeEventType.CANCELLATION;
        if (containsAny(key, "중단", "중지")) return NoticeEventType.SUSPENSION;
        if (containsAny(key, "연장")) return NoticeEventType.EXTENSION;
        if (containsAny(key, "결과", "합격")) return NoticeEventType.RESULT;
        if (containsAny(key, "변경", "정정")) return NoticeEventType.CHANGE;
        if (containsAny(key, "추가")) return NoticeEventType.ADDITION;
        if (containsAny(key, "모집", "채용", "선발")) return NoticeEventType.RECRUITMENT;
        if (containsAny(key, "신청", "접수")) return NoticeEventType.APPLICATION;
        return NoticeEventType.UNKNOWN;
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) return true;
        }
        return false;
    }

    private LinkedHashSet<String> extractModifiers(String defaultOr,
                                                   Set<String> anchors,
                                                   Set<String> aliasKeys) {
        LinkedHashSet<String> modifiers = new LinkedHashSet<>();
        if (defaultOr == null || defaultOr.isBlank()) {
            return modifiers;
        }

        for (String rawTerm : defaultOr.split("\\s*\\|\\s*")) {
            String term = sanitizeTerm(rawTerm);
            if (term.isBlank() || anchors.contains(term) || isQuestionTerm(term)) {
                continue;
            }
            String termKey = SemanticTextNormalizer.lookupKey(term);
            boolean derivedFromAlias = aliasKeys.stream()
                    .anyMatch(alias -> termKey.equals(alias) || termKey.contains(alias));
            if (!derivedFromAlias) {
                modifiers.add(term);
            }
        }
        return modifiers;
    }

    private boolean isQuestionTerm(String term) {
        return QUESTION_TERMS.contains(term)
                || term.startsWith("언제까지")
                || term.endsWith("알려줘");
    }

    private LinkedHashSet<String> sanitizeTerms(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        values.stream().map(this::sanitizeTerm).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private String sanitizeTerm(String value) {
        return value == null
                ? ""
                : value.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String parenthesizeOr(Set<String> values) {
        return "(" + String.join(" | ", values) + ")";
    }

    private static int aliasPosition(String originalKey, String alias) {
        int position = originalKey.indexOf(SemanticTextNormalizer.lookupKey(alias));
        return position < 0 ? Integer.MAX_VALUE : position;
    }
}
