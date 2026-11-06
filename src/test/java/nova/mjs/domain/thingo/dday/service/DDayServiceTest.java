package nova.mjs.domain.thingo.dday.service;

import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import nova.mjs.domain.thingo.calendar.service.MjuCalendarService;
import nova.mjs.domain.thingo.dday.dto.DDayDto;
import nova.mjs.domain.thingo.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DDayServiceTest {

    private final MjuCalendarRepository calendarRepository = mock(MjuCalendarRepository.class);
    private final DDayService service =
            new DDayService(new MjuCalendarService(mock(RestTemplate.class), calendarRepository));

    private List<String> names(Member.StudentType type) {
        LocalDate later = LocalDate.now().plusDays(10);
        List<MjuCalendar> events = List.of(
                event(1L, "[대학원] 지도교수 제청서 제출", later),
                event(2L, "[학부·대학원] 강의평가 기간", later.plusDays(1)),
                event(3L, "[학 부] 재수강신청원 제출 기간", later.plusDays(2)),
                event(4L, "개교기념일", later.plusDays(3)));
        when(calendarRepository.findByEndDateGreaterThanEqualOrderByStartDateAscEndDateAscIdAsc(any()))
                .thenReturn(events);
        return service.getDDays(10, type).stream().map(DDayDto.Response::getEventName).toList();
    }

    private MjuCalendar event(Long id, String description, LocalDate date) {
        return MjuCalendar.builder().id(id).year(date.getYear()).startDate(date).endDate(date)
                .description(description).build();
    }

    @Test
    @DisplayName("학생에게는 [대학원] 전용 일정을 빼고 학부, 공동, 태그 없는 일정만 준다")
    void should_대학원일정_제외_when_학생() {
        assertThat(names(Member.StudentType.STUDENT)).containsExactly(
                "[학부·대학원] 강의평가 기간", "[학 부] 재수강신청원 제출 기간", "개교기념일");
    }

    @Test
    @DisplayName("대학원생에게는 [학부] 전용 일정을 뺀다")
    void should_학부일정_제외_when_대학원생() {
        assertThat(names(Member.StudentType.GRADUATE_STUDENT)).containsExactly(
                "[대학원] 지도교수 제청서 제출", "[학부·대학원] 강의평가 기간", "개교기념일");
    }

    @Test
    @DisplayName("studentType을 안 보내면 구버전 앱처럼 전부 준다")
    void should_전부_when_대상없음() {
        assertThat(names(null)).hasSize(4);
    }
}
