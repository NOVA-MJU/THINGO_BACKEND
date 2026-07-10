package nova.mjs.domain.thingo.report.entity;

import lombok.Getter;

/**
 * 신고 사유.
 *
 * 화면설계서의 6개 고정 항목과 1:1 대응한다.
 * - label: 신고 사유 항목 텍스트
 * - description: 항목별 설명 텍스트 (관리자 메일에 함께 첨부)
 */
@Getter
public enum ReportReason {

    COMMERCIAL_AD(
            "상업적 광고 및 홍보성",
            "영리 목적의 홍보·판매, 타 서비스나 사이트 가입 유도"),
    INAPPROPRIATE(
            "주제 및 서비스 성격에 부적절함",
            "게시판 주제나 장소와 무관한 내용, 무의미한 초성·도배·낚시"),
    ABUSE(
            "욕설/비하/인신공격",
            "특정인이나 단체에 대한 비방, 명예훼손, 학우 간 분란 조장"),
    OBSCENE(
            "음란성/불건전한 내용",
            "선정적인 내용, 불건전한 만남 유도, 불법촬영물 등 유통"),
    PRIVACY_SCAM(
            "개인정보 노출 및 사칭/사기",
            "개인 실명·연락처·SNS ID 노출, 관리자 사칭, 사기 의심"),
    ETC(
            "기타",
            "구체적인 기타 사유를 입력해 주세요.");

    private final String label;
    private final String description;

    ReportReason(String label, String description) {
        this.label = label;
        this.description = description;
    }
}
