package nova.mjs.domain.thingo.semantic;

/**
 * 표준 Topic에 연결되는 학교 공지 별칭.
 *
 * @param absorbsContainedAliases true면 이 별칭 안에 들어 있는 다른 토픽의 짧은 별칭을 독립 근거로 보지 않는다
 *                                ('졸업증명서'가 잡히면 그 안의 '졸업'으로 졸업 토픽을 붙이지 않음). JSON에서 생략하면 false
 */
public record TopicAliasDefinition(String topicId, String alias, int priority, boolean absorbsContainedAliases) {
}
