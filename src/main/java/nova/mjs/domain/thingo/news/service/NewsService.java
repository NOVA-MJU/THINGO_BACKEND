package nova.mjs.domain.thingo.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.news.DTO.NewsResponseDTO;
import nova.mjs.domain.thingo.news.entity.News;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsCrawlerService newsCrawler;

    /** 1) 카테고리별 페이지 조회 (category null/blank = 전체) */
    @Transactional(readOnly = true)
    public Page<NewsResponseDTO> getNewsByCategory(String category, Pageable pageable) {
        Page<News> page;
        if (isBlank(category)) {
            page = newsRepository.findAll(pageable);
        } else {
            News.Category cat = News.Category.fromStringTOUppercase(category);
            page = newsRepository.findByCategory(cat, pageable);
        }
        return page.map(NewsResponseDTO::fromEntity);
    }

    /** 2) 크롤링 후 신규 기사만 저장 */
    @Transactional
    public List<NewsResponseDTO> crawlAndSaveNews(String category) {
        List<News.Category> targets = resolveCategories(category);

        List<News> savedAll = new ArrayList<>();
        for (News.Category cat : targets) {
            List<News> crawled = newsCrawler.crawl(cat);
            if (crawled.isEmpty()) {
                log.info("[{}] 크롤링 결과 없음", cat);
                continue;
            }

            // newsIndex 기준으로 기존 기사 일괄 조회 → 중복 제거
            List<Long> indices = crawled.stream().map(News::getNewsIndex).toList();
            Set<Long> existing = new HashSet<>(newsRepository.findExistingNewsIndexIn(indices));

            List<News> toSave = crawled.stream()
                    .filter(n -> !existing.contains(n.getNewsIndex()))
                    .toList();

            if (toSave.isEmpty()) {
                log.info("[{}] 신규 기사 없음 (크롤링 {}건 모두 기존 데이터)", cat, crawled.size());
                continue;
            }

            List<News> saved = newsRepository.saveAll(toSave);
            savedAll.addAll(saved);
            log.info("[{}] 신규 {}건 저장 (크롤링 {}건, 중복 {}건)",
                    cat, saved.size(), crawled.size(), crawled.size() - saved.size());
        }

        return NewsResponseDTO.fromEntityToList(savedAll);
    }

    /** 3) 카테고리별 또는 전체 삭제 */
    @Transactional
    public void deleteAllNews(String category) {
        if (isBlank(category)) {
            newsRepository.deleteAll();
            log.info("전체 뉴스 삭제 완료");
            return;
        }
        News.Category cat = News.Category.fromStringTOUppercase(category);
        newsRepository.deleteByCategory(cat);
        log.info("[{}] 카테고리 뉴스 삭제 완료", cat);
    }

    private List<News.Category> resolveCategories(String category) {
        if (isBlank(category)) {
            return Arrays.asList(News.Category.values());
        }
        return List.of(News.Category.fromStringTOUppercase(category));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}