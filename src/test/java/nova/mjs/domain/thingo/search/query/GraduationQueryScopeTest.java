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

    @Test
    @DisplayName("학번을 학사안내문 졸업요건 표의 학번 구간 제목으로 바꾼다")
    void mapsCohortToGuideRangeTitle() {
        assertThat(GraduationQueryScope.cohortTitleLabel("21학번 졸업학점")).contains("2018~2024학번");
        assertThat(GraduationQueryScope.cohortTitleLabel("경영학과 2016학번 졸업요건")).contains("2015~2017학번");
        assertThat(GraduationQueryScope.cohortTitleLabel("26학번 졸업학점")).contains("2025학번 이후");
        assertThat(GraduationQueryScope.cohortTitleLabel("10학번 졸업학점")).contains("2009~2014학번");
        assertThat(GraduationQueryScope.cohortTitleLabel("98학번 졸업학점")).isEmpty();
        assertThat(GraduationQueryScope.cohortTitleLabel("21학번 장학금")).isEmpty();
    }
}
