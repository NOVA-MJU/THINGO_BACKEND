package nova.mjs.domain.thingo.search.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalNoticeTitleTest {

    @Test
    @DisplayName("학교 대표 공지 제목은 핵심어가 검색어와 같다")
    void canonicalNoticeMatchesQueryCore() {
        String query = CanonicalNoticeTitle.core("수강신청");
        assertThat(CanonicalNoticeTitle.core("[학사지원] 2026학년도 2학기 수강신청 일정 안내")).isEqualTo(query);
        assertThat(CanonicalNoticeTitle.core("2026학년도 2학기 수강신청 일정 안내")).isEqualTo(query);
        assertThat(CanonicalNoticeTitle.core("2026-2학기 학점포기 신청 안내")).isEqualTo(CanonicalNoticeTitle.core("학점포기"));
    }

    @Test
    @DisplayName("세부 대상 공지는 다른 말이 남아 대표 공지가 아니다")
    void specificNoticesDoNotMatch() {
        String query = CanonicalNoticeTitle.core("수강신청");
        assertThat(CanonicalNoticeTitle.core("2026학년도 2학기 군 KCU 수강신청 및 수강 안내")).isNotEqualTo(query);
        assertThat(CanonicalNoticeTitle.core("2026학년도 2학기 수강신청 철회 안내")).isNotEqualTo(query);
        assertThat(CanonicalNoticeTitle.core("2026학년도 2학기 봉사시간 적립 및 봉사학점 수강신청 및 취득 안내")).isNotEqualTo(query);
        assertThat(CanonicalNoticeTitle.core("안내")).isEmpty();
    }
}
