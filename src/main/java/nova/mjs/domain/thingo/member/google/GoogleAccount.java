package nova.mjs.domain.thingo.member.google;

/**
 * 구글 ID 토큰 검증을 통과한 학교 계정 정보.
 * affiliation은 프로필 이름 "이름 /학생/학부 전공"에서 이름 뒤를 떼어 낸 원문으로, 학과를 찾는 데만 쓴다
 */
public record GoogleAccount(String email, String name, String pictureUrl, String affiliation) {
}
