package nova.mjs.domain.thingo.department.entity.enumList;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentNameTest {

    @Test
    void 학부와_전공이_함께_있으면_전공을_고른다() {
        DepartmentName department = DepartmentName.fromAffiliation("학생/융합소프트웨어학부 데이터사이언스전공");

        assertThat(department).isEqualTo(DepartmentName.DATA_SCIENCE);
        assertThat(department.getCollege()).isEqualTo(College.AI_SOFTWARE);
        assertThat(DepartmentName.fromAffiliation("학생/경영학부 경영학전공")).isEqualTo(DepartmentName.BUSINESS_ADMINISTRATION);
    }

    @Test
    void 전공_없이_학부만_있으면_학부를_고른다() {
        assertThat(DepartmentName.fromAffiliation("학생/융합소프트웨어학부")).isEqualTo(DepartmentName.CONVERGENT_SOFTWARE_STUDIES);
    }

    @Test
    void 다른_학과_이름에_포함된_이름으로_잘못_고르지_않는다() {
        assertThat(DepartmentName.fromAffiliation("학생/미술사학과")).isEqualTo(DepartmentName.ART_HISTORY_DEPARTMENT);
        assertThat(DepartmentName.fromAffiliation("학생/사학과")).isEqualTo(DepartmentName.HISTORY_DEPARTMENT);
    }

    @Test
    void 표기가_조금_달라도_찾는다() {
        assertThat(DepartmentName.fromAffiliation("학생/자율전공학부 (인문)")).isEqualTo(DepartmentName.FREE_MAJOR);
        assertThat(DepartmentName.fromAffiliation("학생/경상 통계학부")).isEqualTo(DepartmentName.ECONOMICS_STATISTICS);
    }

    @Test
    void 찾지_못하면_null이다() {
        assertThat(DepartmentName.fromAffiliation("학생/컴퓨터공학과")).isNull();
        assertThat(DepartmentName.fromAffiliation("기타")).isNull();
        assertThat(DepartmentName.fromAffiliation(null)).isNull();
    }

    @Test
    void 기타를_뺀_모든_학과에_단과대가_있다() {
        for (DepartmentName department : DepartmentName.values()) {
            assertThat(department.getCollege() == null).isEqualTo(department == DepartmentName.OTHER);
        }
    }
}
