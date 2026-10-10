package nova.mjs.domain.thingo.search.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import nova.mjs.domain.thingo.semantic.TopicCatalog;
import nova.mjs.domain.thingo.semantic.NoticeEventType;
import nova.mjs.domain.thingo.semantic.SearchIntent;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryInterpreterTest {

    private SearchQueryInterpreter interpreter;

    @BeforeEach
    void setUp() {
        interpreter = new SearchQueryInterpreter(new TopicCatalog());
    }

    @Test
    @DisplayName("약어는 표준 개념을 후보 필수 조건으로 만든다")
    void abbreviation_becomes_required_concept() {
        SearchQueryPlan plan = interpreter.interpret("국취 신청 언제까지야");

        assertThat(plan.topicIds()).containsExactly("NATIONAL_EMPLOYMENT_SUPPORT");
        assertThat(plan.matchTsQuery()).contains("국민취업지원제도", "국민취업지원", "국취");
        assertThat(plan.matchTsQuery()).doesNotContain("신청");
        assertThat(plan.rankTsQuery()).contains("신청");
        assertThat(plan.rankTsQuery()).doesNotContain("언제까지야");
        assertThat(plan.coverageTsQuery()).contains("& (신청)");
        assertThat(plan.eventType()).isEqualTo(NoticeEventType.APPLICATION);
        assertThat(plan.searchIntent()).isEqualTo(SearchIntent.APPLICATION_PERIOD);
    }

    @Test
    @DisplayName("중간점 표현은 휴학·복학 대안 개념으로 해석한다")
    void middle_dot_expression_becomes_alternatives() {
        List<String> variants = List.of("휴·복학", "휴ㆍ복학", "휴・복학", "휴/복학", "휴학 복학");

        for (String variant : variants) {
            SearchQueryPlan plan = interpreter.interpret(variant + " 신청");
            assertThat(plan.topicIds()).contains("LEAVE_RETURN");
            assertThat(plan.matchTsQuery()).contains("휴학", "복학", "|");
            assertThat(plan.coverageTsQuery()).contains("신청");
        }
    }

    @Test
    @DisplayName("괄호 축약 표현은 신입학·편입학 대안 개념으로 해석한다")
    void parenthesized_expression_becomes_alternatives() {
        SearchQueryPlan plan = interpreter.interpret("신(편)입 장학금");

        assertThat(plan.topicIds()).containsExactly("NEW_TRANSFER_ADMISSION");
        assertThat(plan.matchTsQuery()).contains("신입학", "편입학", "신편입");
        assertThat(plan.matchTsQuery()).doesNotContain("(신입 |", " | 편입 |");
        assertThat(plan.rankTsQuery()).contains("장학금");
        assertThat(plan.coverageTsQuery()).contains("& (장학금)");
    }

    @Test
    @DisplayName("등록되지 않은 일반 검색어는 기존 Komoran OR/AND 계획을 유지한다")
    void ordinary_query_keeps_existing_behavior() {
        SearchQueryPlan plan = interpreter.interpret("도서관 좌석");

        assertThat(plan.hasTopic()).isFalse();
        assertThat(plan.matchTsQuery()).contains("도서관", "좌석", "|");
        assertThat(plan.rankTsQuery()).isEqualTo(plan.matchTsQuery());
        assertThat(plan.coverageTsQuery()).contains("도서관", "좌석", "&");
    }

    @Test
    @DisplayName("취업지원 약어들을 각각의 표준 개념으로 해석한다")
    void employment_abbreviations_resolve() {
        assertConcept("졸특", "GRADUATE_CAREER");
        assertConcept("대플 상담", "UNIVERSITY_JOB_PLUS_CENTER");
        assertConcept("재맞고 신청", "STUDENT_CUSTOM_EMPLOYMENT_SERVICE");
        assertConcept("취준 프로그램", "JOB_PREPARATION");
    }

    @Test
    @DisplayName("'취업계'는 공지에 쓰이는 공식 용어(조기취업 유고결석)로 찾는다")
    void employmentAbsenceSlangResolvesToOfficialTerm() {
        // Komoran이 '취업계'를 '취업'+'계'로 쪼개 '취업'만 든 공지 전체가 후보가 되던 문제.
        // 공지·학사안내문에는 '취업계'가 아니라 '조기취업 유고결석'으로 적혀 있다
        SearchQueryPlan plan = interpreter.interpret("취업계");

        assertThat(plan.topicIds()).containsExactly("EARLY_EMPLOYMENT_ABSENCE");
        assertThat(plan.matchTsQuery()).isEqualTo("(조기취업)");
        assertConcept("조기취업 유고결석 신청", "EARLY_EMPLOYMENT_ABSENCE");
    }

    @Test
    @DisplayName("'병결'은 유고결석 중 입원·진단서가 함께 있는 문서로 찾는다")
    void sickAbsenceSlangRequiresExcusedAbsenceAndIllnessEvidence() {
        // 공지·학사안내문에 '병결'은 없고, 유고결석 사유 '학생의 입원기간 및 치료기간(진단서)'으로 적혀 있다
        SearchQueryPlan plan = interpreter.interpret("병결");

        assertThat(plan.topicIds()).containsExactlyInAnyOrder("ABSENCE_ILLNESS", "EXCUSED_ABSENCE");
        assertThat(plan.matchTsQuery()).contains("유고결석", "입원", "진단서", "&");
        assertConcept("공결", "EXCUSED_ABSENCE");
        assertConcept("결석계 제출", "EXCUSED_ABSENCE");
    }

    @Test
    @DisplayName("증명서 이름 안의 '졸업'·'휴학'은 별도 개념으로 붙지 않는다")
    void certificateNameDoesNotPullInGraduationOrLeaveConcept() {
        for (String query : List.of("졸업증명서", "졸업증명서 발급", "휴학증명서", "성적증명서")) {
            SearchQueryPlan plan = interpreter.interpret(query);
            assertThat(plan.topicIds()).as(query).containsExactly("CERTIFICATE_ISSUANCE");
            assertThat(plan.matchTsQuery()).as(query).isEqualTo("(증명서 | 증명발급)");
        }
        // 증명서와 무관한 '졸업' 단독 검색은 기존대로 졸업 개념이다
        assertThat(interpreter.interpret("졸업").topicIds()).containsExactly("GRADUATION");
    }

    @Test
    @DisplayName("졸업 검색은 학위수여식과 졸업식을 후보에 포함한다")
    void graduationSearchIncludesDegreeCeremonyTerms() {
        SearchQueryPlan plan = interpreter.interpret("졸업");

        assertThat(plan.topicIds()).containsExactly("GRADUATION");
        assertThat(plan.matchTsQuery()).contains("졸업", "학위수여식", "졸업식");
    }

    private void assertConcept(String query, String expectedConceptId) {
        assertThat(interpreter.interpret(query).topicIds()).contains(expectedConceptId);
    }

    @Test
    @DisplayName("해외 단독 검색은 해외·국제 프로그램 전체 토픽으로 해석한다")
    void broadOverseasQueryResolvesGlobalProgram() {
        SearchQueryPlan plan = interpreter.interpret("해외 공고 알려줘");

        assertThat(plan.topicIds()).containsExactly("GLOBAL_PROGRAM");
    }

    @Test
    @DisplayName("구체적인 해외 검색은 세부 토픽을 유지한다")
    void specificOverseasQueryKeepsSpecificTopic() {
        SearchQueryPlan plan = interpreter.interpret("해외 인턴");

        assertThat(plan.topicIds()).contains("GLOBAL_WORK_EXPERIENCE");
        assertThat(plan.topicIds()).doesNotContain("GLOBAL_PROGRAM");
    }

    @Test
    @DisplayName("학과·단과대 약어와 학기교를 서로 다른 필수 개념으로 해석한다")
    void academicAbbreviationsBecomeIndependentRequiredConcepts() {
        SearchQueryPlan plan = interpreter.interpret("미휴 학기교");

        assertThat(plan.topicIds()).containsExactlyInAnyOrder(
                "MEDIA_HUMAN_LIFE_COLLEGE",
                "ACADEMIC_FOUNDATION_GENERAL_EDUCATION");
        assertThat(plan.matchTsQuery()).contains("미디어휴먼라이프대학", "미휴");
        assertThat(plan.matchTsQuery()).contains("학문기초교양", "학기교");
        assertThat(plan.matchTsQuery()).contains(" & ");
    }

    @Test
    @DisplayName("정외·법학 복전 학기교 질의를 세 학사 조건으로 분리한다")
    void multipleMajorAcademicFoundationQueryKeepsAllFacets() {
        SearchQueryPlan plan = interpreter.interpret("정외 법학 복전 학기교");

        assertThat(plan.topicIds()).contains(
                "POLITICAL_SCIENCE_DEPARTMENT",
                "LAW_DEPARTMENT",
                "MULTIPLE_MAJOR",
                "ACADEMIC_FOUNDATION_GENERAL_EDUCATION");
        assertThat(plan.matchTsQuery()).contains("정치외교학전공", "법학과", "복수전공", "학문기초교양");
    }
}
