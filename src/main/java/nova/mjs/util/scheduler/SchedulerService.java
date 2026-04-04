package nova.mjs.util.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.broadcast.service.BroadcastService;
import nova.mjs.domain.thingo.news.service.NewsService;
import nova.mjs.domain.thingo.notice.exception.NoticeCrawlingException;
import nova.mjs.domain.thingo.notice.service.NoticeCrawlingService;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.scheduler.exception.SchedulerCronInvalidException;
import nova.mjs.util.scheduler.exception.SchedulerTaskFailedException;
import nova.mjs.util.scheduler.exception.SchedulerUnknownException;
import nova.mjs.domain.thingo.weather.WeatherService;
import nova.mjs.domain.thingo.weeklyMenu.service.WeeklyMenuService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final NewsService newsService;
    private final WeatherService weatherService;
    private final WeeklyMenuService weeklyMenuService;
    private final NoticeCrawlingService noticeCrawlingService;
    private final BroadcastService broadcastService;

    //날씨 데이터 스케줄링 (매 정각 실행)
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledFetchWeatherData() {
        log.info("[스케쥴러] 매시간 정각 날씨 데이터 업데이트 실행");
        CompletableFuture.runAsync(() -> {
            try {
                weatherService.fetchAndStoreWeatherData();
                log.info("날씨 데이터 업데이트 완료");
            } catch (IllegalArgumentException e) {
                log.error("잘못된 Cron 표현식 오류 : {}", e.getMessage());
                throw new SchedulerCronInvalidException("잘못된 Cron 표현식", ErrorCode.SCHEDULER_CRON_INVALID);
            } catch (Exception e) {
                log.error("날씨 크롤링 중 오류 발생 : {}", e.getMessage());
                throw new SchedulerTaskFailedException("날씨 데이터 업데이트 실패", ErrorCode.SCHEDULER_TASK_FAILED);
            } catch (Throwable t) {
                log.error("알 수 없는 스케줄러 오류 발생 : {}", t.getMessage(), t);
                throw new SchedulerUnknownException("알 수 없는 스케줄러 오류 발생", ErrorCode.SCHEDULER_UNKNOWN_ERROR);
            }
        });
    }

    // 명대신문 크롤링 스케줄링 (매주 월요일 정각마다 실행)
    @Scheduled(cron = "0 0 * * 1 *")
    public void scheduledCrawlNews() {
        log.info("[스케쥴러] 매시간 5분마다 기사 크롤링 실행");
        CompletableFuture.runAsync(() -> {
            try {
                newsService.crawlAndSaveNews(null);
                log.info("기사 데이터 업데이트");
            } catch (IllegalArgumentException e) {
                log.error("잘못된 Cron 표현식 오류 : {}", e.getMessage());
                throw new SchedulerCronInvalidException("잘못된 Cron 표현식입니다.", ErrorCode.SCHEDULER_CRON_INVALID);
            } catch (Exception e) {
                log.error("기사 크롤링 오류 발생 : {}", e.getMessage());
                throw new SchedulerTaskFailedException("기사 크롤링 실패", ErrorCode.SCHEDULER_TASK_FAILED);
            } catch (Throwable t) {
                log.error("알 수 없는 스케줄러 오류 발생 : {}", t.getMessage(), t);
                throw new SchedulerUnknownException("알 수 없는 스케줄러 오류 발생", ErrorCode.SCHEDULER_UNKNOWN_ERROR);
            }
        });
    }

    // 명대 뉴스 데이터 스케쥴링 (매주 화, 목, 토 오전 1시에 실행)
    @Scheduled(cron = "0 0 1 * * TUE,THU,SAT")
    public void scheduledCrawlBroadcastData() {
        log.info("[스케쥴러] 매주 화요일 정각마다 명대 뉴스 업데이트 실행");
        CompletableFuture.runAsync(() -> {
            try {
                broadcastService.syncAllByChannelId();
                log.info("명대 뉴스 업데이트 실행");
            } catch (IllegalArgumentException e) {
                log.error("잘못된 Cron 표현식 오류 : {}", e.getMessage());
                throw new SchedulerCronInvalidException("잘못된 Cron 표현식", ErrorCode.SCHEDULER_CRON_INVALID);
            } catch (Exception e) {
                log.error("명대 뉴스 크롤링 중 오류 발생 : {}", e.getMessage());
                throw new SchedulerTaskFailedException("명대 뉴스 데이터 업데이트 실패", ErrorCode.SCHEDULER_TASK_FAILED);
            } catch (Throwable t) {
                log.error("알 수 없는 스케줄러 오류 발생 : {}", t.getMessage(), t);
                throw new SchedulerUnknownException("알 수 없는 스케줄러 오류 발생", ErrorCode.SCHEDULER_UNKNOWN_ERROR);
            }
        });
    }

    //식단 데이터 크롤링 스케줄링 (매주 토, 일, 월 19:00 실행)
    //@Scheduled(cron = "0 55 22 * * 6") // 테스트 추가
    @Scheduled(cron = "0 0 22 * * 5") // 매주 금요일 22:00시 추가
    @Scheduled(cron = "0 0 10 * * 6") // 매주 토요일 10:00 추가 (금요일에 안올라왔을 경우)
    @Scheduled(cron = "0 0 14 * * 6") // 매주 토요일 14:00 추가 (금요일에 안올라왔을 경우)
    @Scheduled(cron = "0 0 19 * * 6") // 매주 토요일 19:00 실행
    @Scheduled(cron = "0 0 19 * * 7") // 매주 일요일 19:00 실행
    @Scheduled(cron = "0 0 19 * * 1") // 매주 월요일 19:00 실행
    public void scheduledCrawlWeeklyMenu() {
        log.info("[스케쥴러] 매주 토, 일, 월 19시와 추가로 금 22시, 토 10시, 14시에 식단 크롤링 실행");
        CompletableFuture.runAsync(() -> {
            try {
                weeklyMenuService.crawlWeeklyMenu();
                log.info("식단 데이터 업데이트 완료");
            } catch (IllegalArgumentException e) {
                log.error("잘못된 Cron 표현식 오류: {}", e.getMessage());
                throw new SchedulerCronInvalidException("잘못된 Cron 표현식입니다.", ErrorCode.SCHEDULER_CRON_INVALID);
            } catch (Exception e) {
                log.error("식단 크롤링 중 오류 발생 : {}", e.getMessage());
                throw new SchedulerTaskFailedException("식단 데이터 크롤링 실패", ErrorCode.SCHEDULER_TASK_FAILED);
            } catch (Throwable t) {
                log.error("알 수 없는 스케줄러 오류 발생 : {}", t.getMessage(), t);
                throw new SchedulerUnknownException("알 수 없는 스케줄러 오류 발생", ErrorCode.SCHEDULER_UNKNOWN_ERROR);
            }
        });
    }
    // 보통 출근-퇴근 중간에 틈틈히 올리시니까
    // 오전 오후 1시간 ~ 1시간 반 쯤 격차 두고 크롤링
    // 퇴근시간 이후는 크롤링 하지 않고 혹시 전날 저녁에 올라온 게 있을 수 있으니 다음날 8시에 크롤링
    @Scheduled(cron = "0 0 8 * * *")   // 매일 08:00
    @Scheduled(cron = "0 30 9 * * *")  // 매일 09:30
    @Scheduled(cron = "0 30 10 * * *") // 매일 10:30
    @Scheduled(cron = "0 0 12 * * *")  // 매일 12:00
    @Scheduled(cron = "0 30 13 * * *") // 매일 13:30
    @Scheduled(cron = "0 00 15 * * *") // 매일 15:00
    @Scheduled(cron = "0 30 16 * * *") // 매일 16:30
    @Scheduled(cron = "0 0 18 * * *")  // 매일 18:00
    //@Scheduled(cron = "0 36 16 * * *")  // TEST
    public void crawlAllNotices() {
        log.info("[MJS][Scheduler] Notice crawling started.");
        CompletableFuture.runAsync(() -> {
            try {
                noticeCrawlingService.fetchAllNotices();
                log.info("[MJS][Scheduler] Notice crawling completed.");
            } catch (Exception e) {
                /*
                 * 스케줄러 레벨에서는
                 * - 어떤 category가 실패했는지 판단하지 않는다.
                 * - 실패 원인 분석은 Service 로그를 기준으로 한다.
                 */
                log.error("[MJS][Scheduler] Notice crawling failed.", e);
            }
        });
    }
}
