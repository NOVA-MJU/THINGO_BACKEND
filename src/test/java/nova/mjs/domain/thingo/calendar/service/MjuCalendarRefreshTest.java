package nova.mjs.domain.thingo.calendar.service;

import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 학사일정 동기화(refresh)가 멱등인지 검증한다.
 *
 * 예전 구현은 매번 전체 삭제 후 재삽입이라, 스케줄에 걸면 변경이 없어도 id 가 갈리고
 * INSERT 이벤트가 전부 다시 발생해 학사일정 구독자에게 같은 일정이 반복 알림된다.
 */
@ExtendWith(MockitoExtension.class)
class MjuCalendarRefreshTest {

    private static final int YEAR = 2026;

    /** 실제 페이지 구조(#timeTableList > li > dl dt strong, dd .text-list li > strong) 축약본 */
    private static final String HTML = """
            <ul id="timeTableList">
              <li>
                <dl>
                  <dt><strong>3월</strong></dt>
                  <dd>
                    <ul class="text-list">
                      <li><strong>.03 .04 ~ .03 .10</strong> 수강신청 정정기간</li>
                    </ul>
                  </dd>
                </dl>
              </li>
            </ul>
            """;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MjuCalendarRepository calendarRepository;

    @InjectMocks
    private MjuCalendarService service;

    @Test
    @DisplayName("같은 크롤 결과를 다시 받으면 저장도 삭제도 하지 않는다")
    void should_be_idempotent() {
        given(restTemplate.getForObject(anyString(), eq(String.class))).willReturn(HTML);

        // 1회차 - 비어 있는 상태
        given(calendarRepository.findByYear(YEAR)).willReturn(List.of());
        service.refresh(YEAR, YEAR);

        ArgumentCaptor<List<MjuCalendar>> saved = ArgumentCaptor.forClass(List.class);
        verify(calendarRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);

        MjuCalendar inserted = saved.getValue().get(0);
        assertThat(inserted.getStartDate()).isEqualTo(LocalDate.of(YEAR, 3, 4));
        assertThat(inserted.getEndDate()).isEqualTo(LocalDate.of(YEAR, 3, 10));
        assertThat(inserted.getDescription()).contains("수강신청");

        // 2회차 - 같은 내용이 이미 저장된 상태
        given(calendarRepository.findByYear(YEAR)).willReturn(List.of(inserted));
        service.refresh(YEAR, YEAR);

        // saveAll 은 1회차의 1번뿐, 삭제는 한 번도 없음
        verify(calendarRepository, org.mockito.Mockito.times(1)).saveAll(any());
        verify(calendarRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("크롤 결과가 비면 기존 데이터를 지우지 않는다")
    void should_keep_existing_when_crawl_empty() {
        given(restTemplate.getForObject(anyString(), eq(String.class))).willReturn("<html></html>");

        service.refresh(YEAR, YEAR);

        verify(calendarRepository, never()).deleteAll(any());
        verify(calendarRepository, never()).saveAll(any());
        verify(calendarRepository, never()).findByYear(anyInt());
    }
}
