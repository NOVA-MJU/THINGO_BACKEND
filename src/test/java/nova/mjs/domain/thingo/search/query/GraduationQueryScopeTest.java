package nova.mjs.domain.thingo.search.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationQueryScopeTest {

    @Test
    @DisplayName("학번·졸업 일반어만 있으면 학번 공통 기준을 찾는 검색이다")
    void cohortWideWhenOnlyCohortAndGraduationWords() {
        assertThat(GraduationQueryScope.isCohortWide("2025학번 졸업학점")).isTrue();
        assertThat(GraduationQueryScope.isCohortWide("21학번 졸업요건")).isTrue();
        assertThat(GraduationQueryScope.isCohortWide("졸업학점")).isTrue();
        assertThat(GraduationQueryScope.isCohortWide("2019학번 졸업 이수학점은 몇 학점")).isTrue();
    }

    @Test
    @DisplayName("학과·단과대 이름이나 다른 주제가 있으면 학번 공통 검색이 아니다")
    void notCohortWideWhenDepartmentOrOtherTopic() {
        assertThat(GraduationQueryScope.isCohortWide("경영학과 2025학번 졸업학점")).isFalse();
        assertThat(GraduationQueryScope.isCohortWide("컴공 졸업요건")).isFalse();
        assertThat(GraduationQueryScope.isCohortWide("미휴 학기교")).isFalse();
        assertThat(GraduationQueryScope.isCohortWide("졸업작품 전시")).isFalse();
        assertThat(GraduationQueryScope.isCohortWide("장학금 신청")).isFalse();
    }
}
