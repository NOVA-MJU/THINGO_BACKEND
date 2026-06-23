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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NewsCrawlerService {

    private static final String BASE_URL = "https://news.mju.ac.kr";
    private static final String LIST_URL = BASE_URL + "/news/articleList.html?view_type=sm&page=%d";
    private static final String VIEW_URL = BASE_URL + "/news/articleView.html?idxno=%d";
    private static final String DEFAULT_IMAGE = BASE_URL + "/image/logo/snslogo_20191211102712.jpg";

    private static final Pattern IDX_PATTERN = Pattern.compile("idxno=(\\d+)");
    private static final int SUMMARY_MAX_LENGTH = 200;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // 사이트 섹션명 -> 우리 카테고리. 보도/사회만 수집하고, 매핑에 없는 섹션은 수집 제외한다.
    private static final Map<String, News.Category> SECTION_MAP = Map.of(
            "보도", News.Category.REPORT,
            "사회", News.Category.SOCIETY
    );

    /**
     * PHPSESSID 세션을 유지하는 Connection 생성.
     * 이 사이트는 목록 페이지네이션 상태를 서버 세션에 저장하므로,
     * 반드시 동일 세션으로 연속 요청해야 page 파라미터가 반영된다.
     * (세션 없이 요청하면 매번 최신 1페이지로 리셋됨)
     */
    public Connection openSession() {
        return Jsoup.newSession()
                .userAgent(USER_AGENT)
                .referrer(BASE_URL + "/")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                .timeout(15_000)
                .ignoreHttpErrors(true);
    }

    /**
     * 목록 페이지에서 기사 idxno 목록을 최신순으로 추출.
     * 세션을 유지한 채 호출해야 page 파라미터가 정상 동작한다.
     */
    public List<Long> fetchArticleIndices(Connection session, int page) {
        String url = String.format(LIST_URL, page);
        try {
            // 세션 쿠키를 공유하는 새 요청
            Document doc = session.newRequest().url(url).get();
            Elements links = doc.select("section.article-list-content a[href*=articleView.html]");

            LinkedHashSet<Long> indices = new LinkedHashSet<>();
            for (Element link : links) {
                Matcher matcher = IDX_PATTERN.matcher(link.attr("href"));
                if (matcher.find()) {
                    indices.add(Long.parseLong(matcher.group(1)));
                }
            }
            log.info("[목록] page={} - {}건", page, indices.size());
            return new ArrayList<>(indices);
        } catch (IOException e) {
            log.error("[목록] page={} 크롤링 실패", page, e);
            return List.of();
        }
    }

    /**
     * 상세 페이지에서 News 엔티티를 빌드한다.
     * 카테고리는 상세 페이지의 article:section 메타에서만 신뢰성 있게 얻는다.
     * 보도/사회 섹션이 아니면 수집 대상이 아니므로 Optional.empty() 반환.
     */
    public Optional<News> fetchArticle(Connection session, Long newsIndex) {
        String url = String.format(VIEW_URL, newsIndex);
        try {
            Document doc = session.newRequest().url(url).get();

            // 1) 섹션 판별 - 보도/사회만 통과
            String section = meta(doc, "article:section");
            News.Category category = SECTION_MAP.get(section);
            if (category == null) {
                return Optional.empty();
            }

            // 2) 제목 (없으면 유효하지 않은 기사로 간주)
            String title = meta(doc, "og:title");
            if (title == null || title.isBlank()) {
                log.warn("[상세] idxno={} 제목 없음 - 스킵", newsIndex);
                return Optional.empty();
            }

            // 3) 나머지 필드 추출
            String imageUrl = News.toHttps(orDefault(meta(doc, "og:image"), DEFAULT_IMAGE));
            String summary = clip(meta(doc, "og:description"));
            String reporter = orDefault(meta(doc, "og:article:author"), "미상");
            LocalDateTime date = parsePublishedTime(meta(doc, "article:published_time"));
            String link = BASE_URL + "/news/articleView.html?idxno=" + newsIndex;

            return Optional.of(News.builder()
                    .newsIndex(newsIndex)
                    .title(title)
                    .date(date)
                    .reporter(reporter)
                    .imageUrl(imageUrl)
                    .summary(summary)
                    .link(link)
                    .category(category)
                    .build());
        } catch (IOException e) {
            log.warn("[상세] idxno={} 크롤링 실패: {}", newsIndex, e.getMessage());
            return Optional.empty();
        }
    }

    /** OpenGraph/article 메타 태그 content 추출 */
    private String meta(Document doc, String property) {
        Element element = doc.selectFirst("meta[property=\"" + property + "\"]");
        return element == null ? null : element.attr("content").trim();
    }

    private String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String clip(String summary) {
        if (summary == null) return "";
        return summary.length() > SUMMARY_MAX_LENGTH ? summary.substring(0, SUMMARY_MAX_LENGTH) : summary;
    }

    /** ISO-8601(예: 2026-06-01T00:37:12+09:00) 파싱, 실패 시 현재 시각 */
    private LocalDateTime parsePublishedTime(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
