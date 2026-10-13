package nova.mjs.domain.thingo.member.google;

/** 구글 ID 토큰 검증을 통과한 학교 계정 정보 */
public record GoogleAccount(String email, String name, String pictureUrl) {
}
