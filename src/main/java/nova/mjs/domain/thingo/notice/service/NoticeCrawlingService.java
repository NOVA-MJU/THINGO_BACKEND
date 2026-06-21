package nova.mjs.domain.thingo.notice.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.notice.entity.Notice;
import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import nova.mjs.domain.thingo.notice.service.crawl.NoticeCrawlHelper;
import nova.mjs.domain.thingo.notice.service.crawl.NoticeUrlRegistry;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본은 조회: 크롤링은 트랜잭션 밖에서 수행하고, 저장만 별도 트랜잭션으로 수행한다.
public class NoticeCrawlingService {

    /**
     * 수동 운영 데이터 보호 카테고리.
     *
     * - 아래 카테고리는 운영자가 DB에 직접 입력/보정하는 경우가 있어
     *   크롤링 cleanup 대상에서 제외한다.
     */
    private static final Set<String> CLEANUP_EXCLUDED_CATEGORIES = Set.of("rule");

    private final NoticeRepository noticeRepository;
    private final ApplicationContext applicationContext;

    /*
     * 상세 페이지 URL 조립 규칙의 일부.
     * Registry는 "무엇을 크롤링할지(카테고리 -> 목록 path)"만 관리하고,
     * Service는 "어떤 규칙으로 상세 URL을 만들지"를 관리한다.
     */
    private static final String SUBVIEW_BASE =
            "https://www.mju.ac.kr/mjukr/255/subview.do?enc=";

    /**
     * 반복 공지/재게시 정리 기준(휴리스틱)
     * - "최근 1개월" 이내 동일 title 교체 대상 탐색 범위
     * - 운영 안정성을 위해 범위를 반드시 제한한다.
     */
    private static final int DUPLICATE_WINDOW_MONTHS = 1;

    /**
     * 조회수 경량 갱신 대상 윈도우(주).
     * - HOT 공지 집계 기간(1주)과 동일하게 맞춘다. HOT 랭킹에 반영되는 최근 공지의
     *   조회수만 신선하게 유지하면 충분하므로 범위를 1주로 제한한다.
     */
    private static final int VIEWCOUNT_REFRESH_WINDOW_WEEKS = 1;

    /**
     * 조회수 갱신 시 카테고리당 최대 목록 페이지 수.
     * - 상세 페이지를 받지 않는 경량 작업이지만, 목록 요청 자체도 무한정 돌지 않도록 상한을 둔다.
     * - 목록은 최신순이라 최근 1주 공지는 앞쪽 1~2페이지에 모두 포함된다.
     */
    private static final int MAX_VIEWCOUNT_REFRESH_PAGES = 2;

    /**
     * 모든 공지 크롤링 진입점.
     *
     * 설계 의도:
     * 1) 스레드 점유 시간을 최소화하기 위해 "카테고리 단위"로 작업을 쪼갠다.
     * 2) 어떤 카테고리가 실패하더라도 다른 카테고리는 계속 진행하도록 예외를 격리한다.
     * 3) 트랜잭션은 DB 저장 시점에만 짧게 잡는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void fetchAllNotices() {
        // 학교 공지 크롤링
        crawlGroup(NoticeUrlRegistry.schoolNoticeUrls());
        // 학과 공지 크롤링
        crawlGroup(NoticeUrlRegistry.departmentNoticeUrls());
    }

    /**
     * 특정 카테고리(type)만 크롤링
     *
     * @param type 공지 카테고리 (ex: general, academic, law)
     */
    public void fetchNoticesByType(String type) {

        // 1. 학교 공지에서 먼저 찾기
        if (NoticeUrlRegistry.schoolNoticeUrls().containsKey(type)) {
            crawlSingleCategory(type, NoticeUrlRegistry.schoolNoticeUrls().get(type));
            return;
        }

        // 2. 학과 공지에서 찾기
        if (NoticeUrlRegistry.departmentNoticeUrls().containsKey(type)) {
            crawlSingleCategory(type, NoticeUrlRegistry.departmentNoticeUrls().get(type));
            return;
        }

        // 3. 어디에도 없으면 잘못된 요청
        throw new IllegalArgumentException("지원하지 않는 공지 type입니다: " + type);
    }

    /**
     * 공지 그룹 단위 크롤링.
     *
     * 실패 격리 정책:
     * - group 전체를 try-catch로 감싸면, 특정 학과/카테고리의 실패가 group 전체 실패로 확산된다.
     * - 따라서 category 루프 "안쪽"에서 try-catch를 수행하여, 실패 단위를 category로 고정한다.
     */
    private void crawlGroup(Map<String, String> noticeUrls) {
        noticeUrls.forEach((category, path) -> {
            try {
                crawlSingleCategory(category, path);
            } catch (Exception e) {
                log.error("[MJS] category={} 크롤링 실패. 다른 카테고리는 계속 진행합니다.", category, e);
            }
        });
    }

    /**
     * 특정 카테고리 크롤링.
     *
     * 성능/스레드 관점 핵심:
     * - 네트워크 I/O(목록/본문 HTTP 요청)는 시간이 오래 걸리며 스레드를 점유한다.
     * - 따라서 크롤링 단계에서는 트랜잭션을 열지 않는다.
     * - 크롤링 결과를 메모리 버퍼에 모아두었다가 마지막에 saveAll로 저장한다.
     *
     * 중단 정책:
     * - 오래된 공지를 만나면 더 이상 볼 필요가 없으므로 즉시 중단한다.
     */
    private void crawlSingleCategory(String category, String path) {

        int cutoffYear = LocalDate.now().getYear() - 2;
        int page = 1;
        boolean stop = false;

        /*
         * 저장 버퍼.
         * - DB 저장은 마지막에 한 번만 수행하여 트랜잭션 및 flush 횟수를 줄인다.
         */
        List<Notice> toSave = new ArrayList<>(32);

        /*
         * (선택) 최근 1개월 목록 동기화를 위한 링크 수집.
         * - 크롤링 결과에 없는 DB row를 정리하는 cleanup에 사용한다.
         * - 최근 범위만 다루기 때문에 리스트가 커지지 않는다.
         */
        LocalDateTime recentThreshold = LocalDateTime.now().minusMonths(DUPLICATE_WINDOW_MONTHS);
        Set<String> crawledLinksRecent = new HashSet<>(128);

        while (!stop) {

            /*
             * 목록 페이지 크롤링.
             * - Helper에서 네트워크/파싱/셀렉터를 담당한다.
             */
            Elements rows = NoticeCrawlHelper.crawlList(path, page);
            if (rows.isEmpty()) {
                break;
            }

            for (Element row : rows) {
                /*
                 * row 처리 결과가 stop이면, 현재 카테고리 크롤링을 즉시 종료한다.
                 */
                stop = processRow(row, category, cutoffYear, recentThreshold, crawledLinksRecent, toSave);
                if (stop) {
                    break;
                }
            }

            page++;
        }

        /*
         * DB 저장은 마지막에 한 번만 수행한다.
         */
        applicationContext
                .getBean(NoticeCrawlingService.class)
                .saveNotices(toSave);

        /*
         * (선택) 최근 1개월 범위 동기화(cleanup)
         * - 크롤링 목록에 없는 row를 제거한다.
         * - 오래된 데이터는 건드리지 않는다.
         */
        applicationContext
                .getBean(NoticeCrawlingService.class)
                .cleanupRecentNotices(category, recentThreshold, crawledLinksRecent);

        log.info("[MJS] category={} 크롤링 종료. 저장 대상 {}건, 최근 링크 {}건",
                category, toSave.size(), crawledLinksRecent.size());
    }

    /**
     * 목록 페이지의 단일 row를 처리한다.
     *
     * 처리 목표:
     * - "진짜 새로운 공지"만 DB 저장 대상으로 선별한다.
     * - 중복/반복 공지는 최대한 이른 단계에서 차단하여
     *   불필요한 네트워크 I/O(상세 페이지 크롤링)를 수행하지 않는다.
     *
     * 중단(return true)과 스킵(return false)의 의미:
     * - return true  : 이 row 이후의 공지는 더 이상 의미가 없다고 판단하여
     *                  현재 카테고리 크롤링 자체를 종료한다.
     * - return false : 이 row만 건너뛰고 다음 row 처리를 계속한다.
     */
    private boolean processRow(
            Element row,
            String category,
            int cutoffYear,
            LocalDateTime recentThreshold,
            Set<String> crawledLinksRecent,
            List<Notice> toSave
    ) {

        // (1) 목록 페이지에서 최소한의 메타 정보 추출
        // - 이 단계에서는 가벼운 문자열 파싱만 수행한다.
        String rawDate = row.select("._artclTdRdate").text();
        String rawTitle = row.select(".artclLinkView strong").text();
        String rawLink = row.select(".artclLinkView").attr("href");

        int viewCount = parseViewCount(row);

        LocalDateTime date = normalizeDate(rawDate);
        String title = normalizeTitle(rawTitle);

        /*
         * (1-1) 파싱 결과 검증
         * - 날짜나 제목이 없는 row는 비정상 데이터로 간주한다.
         * - 이는 시스템 오류가 아닌 "원본 HTML 품질 문제"이므로
         *   전체 크롤링을 중단하지 않고 해당 row만 스킵한다.
         */
        if (date == null || title.isEmpty()) {
            return false;
        }

        /*
         * (2) 오래된 공지 중단 조건
         *
         * - 목록은 최신 → 과거 순으로 내려온다고 가정한다.
         * - cutoffYear 이전의 공지를 만나면
         *   이후 페이지에는 더 최신 공지가 존재하지 않으므로
         *   현재 카테고리 크롤링을 즉시 종료한다.
         */
        if (date.getYear() <= cutoffYear) {
            return true;
        }

        /*
         * (3-1) 상세 페이지 URL(enc) 생성
         *
         * - enc 값은 공지 게시글의 기술적 식별자 역할을 한다.
         * - 동일 enc는 "완전히 동일한 게시글"로 간주한다.
         */
        String finalUrl = SUBVIEW_BASE + encodeArtclViewToEnc(rawLink);

        /*
         * (3-1-a) 최근 범위 링크 수집
         * - cleanup(최근 1개월 동기화)에 사용한다.
         */
        if (!date.isBefore(recentThreshold)) {
            crawledLinksRecent.add(finalUrl);
        }

        /*
         * (3-2) 완전 중복 차단
         *
         * 조건:
         * - 동일 category
         * - 동일 상세 link(enc)
         *
         * 의미:
         * - 이미 DB에 저장된 동일 게시글이므로
         *   상세 페이지 크롤링을 수행할 필요가 없다.
         *
         * 처리 방식:
         * - 이 row만 스킵하고 다음 row를 계속 처리한다.
         */
        if (noticeRepository.existsByCategoryAndLink(category, finalUrl)) {
            return false;
        }

        /*
         * (3-3) "동일 title 재게시" 교체 전략
         *
         * 배경:
         * - 직원이 잘못 올렸다가 다시 올리는 케이스가 존재한다.
         * - 이 경우 title은 동일하지만 link(enc)는 새로 생성된다.
         *
         * 전략:
         * - 최근 1개월 내 동일 title의 최신 공지가 존재하면,
         *   기존 DB row를 제거하고 "새 link 공지로 교체"한다.
         *
         * 주의:
         * - 오래된 히스토리를 삭제하지 않기 위해 최근 1개월로 제한한다.
         * - 동일 title이지만 서로 다른 진짜 공지가 존재할 수 있다는 리스크는
         *   "최근 1개월 제한"으로 운영 리스크를 최소화한다.
         */
        LocalDateTime oneMonthAgo = date.minusMonths(DUPLICATE_WINDOW_MONTHS);

        noticeRepository
                .findTopByCategoryAndTitleAndDateAfterOrderByDateDesc(category, title, oneMonthAgo)
                .ifPresent(existing -> {
                    /*
                     * 교체 조건:
                     * - title은 동일
                     * - link(enc)는 다름
                     *
                     * 동작:
                     * - 기존 row를 제거하고 최신 공지로 교체한다.
                     * - 실제 저장은 toSave에 의해 일괄 insert된다.
                     */
                    if (!finalUrl.equals(existing.getLink())) {
                        noticeRepository.delete(existing);
                        log.info("[MJS][NOTICE][REPLACE] category={} title='{}' oldLink={} newLink={}",
                                category, title, existing.getLink(), finalUrl);
                    }
                });

        /*
         * (4) 상세 페이지 본문 크롤링
         *
         * - 여기까지 도달한 경우에만
         *   "새롭고 의미 있는 공지"라고 판단한다.
         * - 네트워크 I/O가 가장 비싼 단계이므로
         *   모든 중복/교체 판단 이후에 수행한다.
         */
        String content = NoticeCrawlHelper.crawlContent(finalUrl);

        /*
         * (5) 엔티티 생성 및 저장 버퍼 적재
         *
         * - 실제 DB 저장은 카테고리 크롤링이 끝난 후
         *   saveAll()로 한 번에 수행된다.
         */
        toSave.add(
                Notice.createNotice(
                        title,
                        content,
                        date,
                        category,
                        finalUrl,
                        viewCount
                )
        );

        // 다음 row 처리를 계속한다.
        return false;
    }

    /**
     * DB 저장 전용 트랜잭션.
     *
     * 주의:
     * - 이 메서드가 호출되는 시점에는 이미 네트워크 I/O 작업이 끝난 상태여야 한다.
     * - 트랜잭션 안에서 HTTP 요청을 수행하면, 커넥션과 트랜잭션이 불필요하게 오래 유지되어 성능이 악화된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveNotices(List<Notice> notices) {
        if (!notices.isEmpty()) {
            noticeRepository.saveAll(notices);
        }
    }

    /**
     * (선택) 최근 1개월 범위 동기화(cleanup)
     *
     * 목적:
     * - "목록에 더 이상 존재하지 않는 공지"가 DB에 남아있는 경우를 정리한다.
     *
     * 주의:
     * - 반드시 최근 범위로 제한한다(운영 안정성).
     * - 링크 목록이 비어있으면 실수로 전부 지우는 사고가 날 수 있으므로
     *   안전장치로 early return 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void cleanupRecentNotices(String category, LocalDateTime threshold, Set<String> links) {
        if (CLEANUP_EXCLUDED_CATEGORIES.contains(category)) {
            log.info("[MJS][NOTICE][CLEANUP] category={} skipped (manual-data protected)", category);
            return;
        }

        if (links == null || links.isEmpty()) {
            log.warn("[MJS][NOTICE][CLEANUP] category={} skipped (links is empty)", category);
            return;
        }

        int deleted = noticeRepository.deleteRecentNotInLinks(category, threshold, links);
        if (deleted > 0) {
            log.info("[MJS][NOTICE][CLEANUP] category={} deleted={} (threshold={})", category, deleted, threshold);
        }
    }

    /* ===================== 조회수 경량 갱신 ===================== */

    /**
     * 최근 공지 조회수 경량 갱신 진입점.
     *
     * 설계 의도:
     * - 전체 크롤링(fetchAllNotices)은 상세 본문까지 받아 비싸므로 자주 돌릴 수 없다.
     * - HOT 공지 랭킹은 조회수에 민감하므로, 상세는 받지 않고 "목록의 조회수"만 긁어
     *   기존 DB row의 viewCount만 갱신하는 경량 작업을 별도로 둔다.
     * - 크롤링은 트랜잭션 밖(NOT_SUPPORTED)에서 수행하고, DB 반영만 별도 트랜잭션으로 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshRecentViewCounts() {
        // 학교 공지 조회수 갱신
        refreshGroupViewCounts(NoticeUrlRegistry.schoolNoticeUrls());
        // 학과 공지 조회수 갱신
        refreshGroupViewCounts(NoticeUrlRegistry.departmentNoticeUrls());
    }

    /**
     * 공지 그룹 단위 조회수 갱신.
     *
     * 실패 격리:
     * - 특정 카테고리의 실패가 다른 카테고리로 확산되지 않도록 category 루프 안쪽에서 예외를 격리한다.
     */
    private void refreshGroupViewCounts(Map<String, String> noticeUrls) {
        noticeUrls.forEach((category, path) -> {
            try {
                refreshSingleCategoryViewCounts(category, path);
            } catch (Exception e) {
                log.error("[MJS][NOTICE][VIEWCOUNT] category={} 조회수 갱신 실패. 다른 카테고리는 계속 진행합니다.", category, e);
            }
        });
    }

    /**
     * 특정 카테고리 조회수 갱신.
     *
     * 처리 흐름:
     * 1) 목록 페이지만 크롤링한다(상세 본문 요청 없음).
     * 2) 최근 윈도우(1주) 안의 row만 link -> viewCount 맵에 모은다.
     * 3) 목록은 최신순이므로 윈도우보다 오래된 row를 만나면 즉시 중단한다.
     * 4) 모은 맵을 별도 트랜잭션에서 일괄 갱신한다.
     *
     * (package-private: 단위 테스트에서 카테고리 단위로 직접 검증하기 위함)
     */
    void refreshSingleCategoryViewCounts(String category, String path) {

        LocalDateTime threshold = LocalDateTime.now().minusWeeks(VIEWCOUNT_REFRESH_WINDOW_WEEKS);
        Map<String, Integer> linkToViewCount = new HashMap<>(64);

        int page = 1;
        boolean stop = false;

        while (!stop && page <= MAX_VIEWCOUNT_REFRESH_PAGES) {

            Elements rows = NoticeCrawlHelper.crawlList(path, page);
            if (rows.isEmpty()) {
                break;
            }

            for (Element row : rows) {
                String rawDate = row.select("._artclTdRdate").text();
                LocalDateTime date = normalizeDate(rawDate);

                // 날짜 파싱 실패 row는 비정상 데이터로 보고 스킵한다(전체 중단 아님).
                if (date == null) {
                    continue;
                }

                // 최신순 목록이므로 윈도우보다 오래된 공지를 만나면 이후는 볼 필요가 없다.
                if (date.isBefore(threshold)) {
                    stop = true;
                    break;
                }

                String rawLink = row.select(".artclLinkView").attr("href");
                if (rawLink.isBlank()) {
                    continue;
                }

                // 저장된 link와 동일한 enc 규칙으로 상세 URL을 생성해 키로 사용한다.
                String finalUrl = SUBVIEW_BASE + encodeArtclViewToEnc(rawLink);
                linkToViewCount.put(finalUrl, parseViewCount(row));
            }

            page++;
        }

        if (linkToViewCount.isEmpty()) {
            return;
        }

        // DB 반영은 별도 트랜잭션으로 일괄 수행한다.
        int updated = applicationContext
                .getBean(NoticeCrawlingService.class)
                .applyViewCountUpdates(category, linkToViewCount);

        log.info("[MJS][NOTICE][VIEWCOUNT] category={} 조회수 갱신 {}건 / 수집 {}건",
                category, updated, linkToViewCount.size());
    }

    /**
     * 조회수 갱신 전용 트랜잭션.
     *
     * 주의:
     * - 이 메서드 호출 시점에는 이미 목록 크롤링(네트워크 I/O)이 끝나 있어야 한다.
     * - updateViewCount는 bulk update라 대상 row가 없으면 0을 반환(무해)하므로 존재 여부 사전 조회가 불필요하다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int applyViewCountUpdates(String category, Map<String, Integer> linkToViewCount) {
        int updated = 0;
        for (Map.Entry<String, Integer> entry : linkToViewCount.entrySet()) {
            updated += noticeRepository.updateViewCount(category, entry.getKey(), entry.getValue());
        }
        return updated;
    }

    /* ===================== 문자열 정규화 ===================== */

    /**
     * 목록 row에서 조회수를 추출한다.
     * - 조회수 컬럼은 보통 6번째 td(index 5)이며, 컬럼 수가 적은 변형 테이블은 마지막 td를 사용한다.
     * - 숫자 외 문자는 제거하고, 값이 없으면 0으로 본다.
     */
    private int parseViewCount(Element row) {
        Elements tds = row.select("td");
        String rawViewCount = "0";

        if (tds.size() >= 6) {
            rawViewCount = tds.get(5).text();
        } else if (!tds.isEmpty()) {
            rawViewCount = tds.last().text();
        }

        String numbersOnly = rawViewCount.replaceAll("[^0-9]", "");
        return numbersOnly.isEmpty() ? 0 : Integer.parseInt(numbersOnly);
    }

    private LocalDateTime normalizeDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
        try {
            return LocalDate.parse(
                    rawDate.trim()
                            .replaceAll("\\s+", "")
                            .replaceAll("\\.\\s*", "-")
            ).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeTitle(String rawTitle) {
        return rawTitle == null ? "" : rawTitle.trim().replaceAll("\\s+", " ");
    }

    /* ===================== 상세 URL enc 생성 ===================== */

    /**
     * 공지 상세 페이지 enc 파라미터 생성.
     *
     * 설계 의도:
     * - 이 메서드는 순수 문자열 처리 로직만 수행한다.
     * - 네트워크 I/O, DB 접근, 트랜잭션과 무관하다.
     * - 상세 URL 생성 규칙이 바뀌면 이 메서드만 수정하면 된다.
     */
    private String encodeArtclViewToEnc(String rawLink) {

        String path = rawLink.split("\\?")[0];
        if (!path.startsWith("/")) path = "/" + path;

        String query =
                "?page=1&srchColumn=&srchWrd=&bbsClSeq=&bbsOpenWrdSeq=" +
                        "&rgsBgndeStr=&rgsEnddeStr=&isViewMine=false&isView=true&password=";

        String full = "fnct1|@@|" + path + query;

        return URLEncoder.encode(
                Base64.getEncoder().encodeToString(full.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8
        );
    }
}
