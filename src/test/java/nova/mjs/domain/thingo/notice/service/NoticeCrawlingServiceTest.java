package nova.mjs.domain.thingo.notice.service;

import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import nova.mjs.domain.thingo.notice.service.crawl.NoticeCrawlHelper;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공지 조회수 경량 갱신(refreshSingleCategoryViewCounts) 단위 테스트.
 * - 목록 크롤링은 정적 Helper(NoticeCrawlHelper)이므로 mockStatic으로 대체한다.
 * - 자기 호출(applicationContext.getBean)은 자기 자신을 반환하도록 stub하여 실제 갱신 로직을 태운다.
 */
@ExtendWith(MockitoExtension.class)
class NoticeCrawlingServiceTest {

    @Mock
    NoticeRepository noticeRepository;

    @Mock
    ApplicationContext applicationContext;

    @InjectMocks
    NoticeCrawlingService noticeCrawlingService;

    private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ofPattern("yyyy. MM. dd");

    // 목록 row 한 줄 HTML 생성. 실제 mju.ac.kr 목록 구조(조회수=6번째 td, 날짜=_artclTdRdate)를 모사한다.
    private String row(LocalDate date, String title, String href, int views) {
        return "<tr>"
                + "<td>1</td>"
                + "<td><a class=\"artclLinkView\" href=\"" + href + "\"><strong>" + title + "</strong></a></td>"
                + "<td>작성자</td>"
                + "<td class=\"_artclTdRdate\">" + LIST_DATE.format(date) + "</td>"
                + "<td>분류</td>"
                + "<td>" + views + "</td>"
                + "</tr>";
    }

    private Elements rows(String... rowFragments) {
        StringBuilder html = new StringBuilder("<table><tbody>");
        for (String fragment : rowFragments) {
            html.append(fragment);
        }
        html.append("</tbody></table>");
        return Jsoup.parse(html.toString()).select("tbody > tr");
    }

    @Test
    @DisplayName("최근 윈도우(1주) 내 공지의 조회수를 updateViewCount로 갱신한다")
    void should_updateViewCount_forRecentRows() {
        // given
        Elements page1 = rows(row(LocalDate.now().minusDays(1), "최근공지", "/bbs/a/artclView.do?x=1", 123));
        try (MockedStatic<NoticeCrawlHelper> mocked = mockStatic(NoticeCrawlHelper.class)) {
            mocked.when(() -> NoticeCrawlHelper.crawlList(eq("p"), eq(1))).thenReturn(page1);
            mocked.when(() -> NoticeCrawlHelper.crawlList(eq("p"), eq(2))).thenReturn(new Elements());
            when(applicationContext.getBean(NoticeCrawlingService.class)).thenReturn(noticeCrawlingService);
            when(noticeRepository.updateViewCount(anyString(), anyString(), anyInt())).thenReturn(1);

            // when
            noticeCrawlingService.refreshSingleCategoryViewCounts("general", "p");

            // then
            verify(noticeRepository).updateViewCount(eq("general"), anyString(), eq(123));
        }
    }

    @Test
    @DisplayName("윈도우(1주)보다 오래된 공지를 만나면 이후 row는 갱신하지 않고 중단한다")
    void should_stop_whenOlderThanWindow() {
        // given - 최신순: 첫 row 최근(포함), 둘째 row 8일 전(윈도우 밖)
        Elements page1 = rows(
                row(LocalDate.now().minusDays(1), "최근", "/bbs/a/artclView.do?x=1", 10),
                row(LocalDate.now().minusDays(8), "오래됨", "/bbs/a/artclView.do?x=2", 999)
        );
        try (MockedStatic<NoticeCrawlHelper> mocked = mockStatic(NoticeCrawlHelper.class)) {
            mocked.when(() -> NoticeCrawlHelper.crawlList(eq("p"), eq(1))).thenReturn(page1);
            when(applicationContext.getBean(NoticeCrawlingService.class)).thenReturn(noticeCrawlingService);
            when(noticeRepository.updateViewCount(anyString(), anyString(), anyInt())).thenReturn(1);

            // when
            noticeCrawlingService.refreshSingleCategoryViewCounts("general", "p");

            // then - 최근 1건만 갱신, 오래된 999는 미갱신, 2페이지는 크롤링하지 않음(중단)
            verify(noticeRepository, times(1)).updateViewCount(eq("general"), anyString(), eq(10));
            verify(noticeRepository, never()).updateViewCount(eq("general"), anyString(), eq(999));
            mocked.verify(() -> NoticeCrawlHelper.crawlList(eq("p"), eq(2)), never());
        }
    }

    @Test
    @DisplayName("수집된 공지가 없으면 갱신(updateViewCount)을 호출하지 않는다")
    void should_notCallUpdate_whenNothingCollected() {
        // given - 빈 목록
        try (MockedStatic<NoticeCrawlHelper> mocked = mockStatic(NoticeCrawlHelper.class)) {
            mocked.when(() -> NoticeCrawlHelper.crawlList(eq("p"), eq(1))).thenReturn(new Elements());

            // when
            noticeCrawlingService.refreshSingleCategoryViewCounts("general", "p");

            // then
            verify(noticeRepository, never()).updateViewCount(anyString(), anyString(), anyInt());
        }
    }
}
