package nova.mjs.domain.thingo.news.service;

import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.news.entity.News;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NewsCrawlerService {

    private static final String BASE_URL = "https://news.mju.ac.kr";
    private static final String LIST_URL_FMT =
            BASE_URL + "/news/articleList.html?sc_section_code=%s&view_type=sm&page=%d";

    private static final Map<News.Category, String> SECTION_CODES = Map.of(
            News.Category.REPORT, "S1N1",
            News.Category.SOCIETY, "S1N3"
    );

    private static final Pattern IDX_PATTERN = Pattern.compile("idxno=(\\d+)");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{4}[-./]\\d{2}[-./]\\d{2}(?:\\s+\\d{2}:\\d{2})?)");
    private static final Pattern REPORTER_PATTERN =
            Pattern.compile("([가-힣]{2,4}\\s*(?:수습기자|기자|편집장|부장|국장))");

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_IMAGE = BASE_URL + "/image/logo/snslogo_20191211102712.jpg";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // 사이드바/메뉴 영역 제외 (이 안에 있는 링크는 본문 기사가 아님)
    private static final String EXCLUDE_PARENTS =
            ":not(aside *):not(footer *):not(nav *)" +
                    ":not(.menu *):not(.user-menu *):not(.no-bullet *)" +
                    ":not(.vertical *):not(.pagination *):not(.address *)";

    public List<News> crawl(News.Category category) {
        return crawl(category, 1);
    }

    public List<News> crawl(News.Category category, int page) {
        String sectionCode = SECTION_CODES.get(category);
        if (sectionCode == null) {
            throw new IllegalArgumentException("지원하지 않는 카테고리입니다: " + category);
        }

        String url = String.format(LIST_URL_FMT, sectionCode, page);
        log.info("[{}] 크롤링 시작 - {}", category, url);

        try {
            Connection.Response resp = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .referrer("https://news.mju.ac.kr/")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .timeout(15_000)
                    .ignoreHttpErrors(true)
                    .execute();

            if (resp.statusCode() != 200) {
                log.warn("[{}] HTTP {} 응답", category, resp.statusCode());
                return List.of();
            }

            Document doc = resp.parse();
            List<News> result = extractArticles(doc, category);
            log.info("[{}] 크롤링 완료 - {}건", category, result.size());
            return result;

        } catch (IOException e) {
            log.error("[{}] 크롤링 실패", category, e);
            return List.of();
        }
    }

    /**
     * articleView 링크를 출발점으로 컨테이너를 자동 탐지하여 기사 추출.
     * 사이트 마크업 변경에 robust한 방식.
     */
    private List<News> extractArticles(Document doc, News.Category category) {
        // 1) 사이드바/네비게이션 영역을 제외한 본문의 articleView 링크만 수집
        Elements links = doc.select("a[href*=articleView.html]" + EXCLUDE_PARENTS);
        if (links.isEmpty()) {
            // 제외 셀렉터가 너무 강하게 걸렸으면 전체 링크로 대체
            links = doc.select("a[href*=articleView.html]");
        }

        // 2) idxno로 dedupe + 정보 풍부도가 가장 높은 컨테이너 선택
        //    (한 기사당 이미지 링크 / 제목 링크 / 요약 링크 등 여러 개가 잡히기 때문)
        Map<Long, ArticleHit> bestByIdx = new LinkedHashMap<>();
        for (Element link : links) {
            Matcher m = IDX_PATTERN.matcher(link.attr("href"));
            if (!m.find()) continue;
            Long idx = Long.parseLong(m.group(1));

            Element container = findArticleContainer(link);
            if (container == null) continue;

            ArticleHit hit = new ArticleHit(link, container);
            ArticleHit prev = bestByIdx.get(idx);
            if (prev == null || hit.score() > prev.score()) {
                bestByIdx.put(idx, hit);
            }
        }

        // 3) 본문 기사로 인정 (점수 기준: 사이드바 단순 링크는 점수 낮음)
        List<News> result = new ArrayList<>();
        for (Map.Entry<Long, ArticleHit> e : bestByIdx.entrySet()) {
            ArticleHit hit = e.getValue();
            if (hit.score() < 2) continue;  // 메타 정보가 부족한 항목은 제외

            try {
                News news = buildNews(e.getKey(), hit, category);
                if (news != null) result.add(news);
            } catch (Exception ex) {
                log.warn("기사 빌드 실패 idx={}: {}", e.getKey(), ex.getMessage());
            }
        }

        if (result.isEmpty()) logEmpty(doc, category);
        return result;
    }

    /** 링크의 부모를 거슬러 올라가며 날짜 패턴이 있는 가장 가까운 컨테이너 반환 */
    private Element findArticleContainer(Element link) {
        Element cur = link;
        for (int depth = 0; depth < 6 && cur != null; depth++) {
            String text = cur.text();
            if (text.length() > 30 && DATE_PATTERN.matcher(text).find()) {
                return cur;
            }
            cur = cur.parent();
        }
        return null;
    }

    private News buildNews(Long idx, ArticleHit hit, News.Category category) {
        Element link = hit.link;
        Element container = hit.container;

        String href = link.attr("href");
        String fullLink = href.startsWith("http") ? href : BASE_URL + href;

        // 제목: 점수가 가장 높은 컨테이너 안의 같은 idxno 링크 중 텍스트가 가장 긴 것
        String title = container.select("a[href*=idxno=" + idx + "]").stream()
                .map(a -> a.text().trim())
                .filter(s -> !s.isBlank())
                .max(Comparator.comparingInt(String::length))
                .orElse("");
        if (title.isBlank()) return null;

        String containerText = container.text();

        // 날짜
        LocalDateTime date = LocalDateTime.now();
        Matcher dm = DATE_PATTERN.matcher(containerText);
        if (dm.find()) date = parseDate(dm.group(1));

        // 기자명
        String reporter = "미상";
        Matcher rm = REPORTER_PATTERN.matcher(containerText);
        if (rm.find()) reporter = rm.group(1).replaceAll("\\s+", " ").trim();

        // 요약: 컨테이너 텍스트에서 제목/기자/날짜/구분자 제거 후 남은 부분
        String summary = containerText
                .replace(title, "")
                .replaceAll(DATE_PATTERN.pattern(), "")
                .replaceAll(REPORTER_PATTERN.pattern(), "")
                .replaceAll("[|·]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (summary.length() > 200) summary = summary.substring(0, 200);

        // 썸네일
        String imageUrl = DEFAULT_IMAGE;
        Element img = container.selectFirst("img");
        if (img != null) {
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.attr("src");
            if (src != null && !src.isBlank()) {
                imageUrl = src.startsWith("http") ? src : BASE_URL + src;
            }
        }

        return News.builder()
                .newsIndex(idx)
                .title(title)
                .date(date)
                .reporter(reporter)
                .imageUrl(imageUrl)
                .summary(summary)
                .link(fullLink)
                .category(category)
                .build();
    }

    private LocalDateTime parseDate(String raw) {
        String s = raw.replace(".", "-").trim();
        try {
            if (s.length() >= 16) return LocalDateTime.parse(s.substring(0, 16), DATE_TIME_FMT);
            return LocalDate.parse(s.substring(0, 10), DATE_ONLY_FMT).atStartOfDay();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /** 0건일 때 추가 진단: 첫 articleView 링크의 부모 체인 출력 */
    private void logEmpty(Document doc, News.Category category) {
        log.warn("[{}] 추출 결과 0건", category);
        Elements links = doc.select("a[href*=articleView.html]");
        log.warn("  - 전체 articleView 링크: {}건", links.size());
        if (!links.isEmpty()) {
            Element cur = links.first();
            log.warn("  - 첫 링크 부모 체인:");
            for (int i = 0; i < 5 && cur != null; i++) {
                log.warn("    [{}] <{} class='{}' id='{}'>",
                        i, cur.tagName(), cur.className(), cur.id());
                cur = cur.parent();
            }
        }
    }

    /** 링크와 그 컨테이너 + 정보 풍부도 점수 */
    private static class ArticleHit {
        final Element link;
        final Element container;

        ArticleHit(Element link, Element container) {
            this.link = link;
            this.container = container;
        }

        int score() {
            String text = container.text();
            int s = 0;
            if (text.length() > 50) s++;
            if (DATE_PATTERN.matcher(text).find()) s++;
            if (REPORTER_PATTERN.matcher(text).find()) s++;
            if (container.selectFirst("img") != null) s++;
            return s;
        }
    }
}