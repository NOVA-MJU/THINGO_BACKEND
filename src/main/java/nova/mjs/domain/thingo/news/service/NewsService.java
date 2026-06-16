package nova.mjs.domain.thingo.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.news.DTO.NewsResponseDTO;
import nova.mjs.domain.thingo.news.entity.News;
import nova.mjs.domain.thingo.news.repository.NewsRepository;
import org.jsoup.Connection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepository newsRepository;
    private final NewsCrawlerService newsCrawler;

    // 증분 크롤링 시 훑을 최대 목록 페이지 수 (신규가 더 없으면 조기 중단)
    private static final int INCREMENTAL_MAX_PAGES = 10;
    // 백필 안전 상한 (전체 약 339페이지)
    private static final int BACKFILL_MAX_PAGES = 500;
    // 백필 상세 요청 간 예의상 지연(ms)
    private static final long BACKFILL_DELAY_MS = 200L;

    /** 카테고리별 페이지 조회 (category null/blank = 전체) */
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

    /**
     * 증분 크롤링: 목록을 최신 페이지부터 훑으며 신규 기사만 저장.
     * 한 페이지의 idxno가 모두 기존 데이터면(더 옛날도 이미 저장됨) 조기 중단한다.
     * 네트워크 호출이 포함되므로 메서드 자체에는 @Transactional을 걸지 않는다.
     * (저장은 JpaRepository 호출 단위로 각각 트랜잭션 처리)
     */
    public List<NewsResponseDTO> crawlLatest() {
        Connection session = newsCrawler.openSession();
        List<News> savedAll = new ArrayList<>();

        for (int page = 1; page <= INCREMENTAL_MAX_PAGES; page++) {
            List<Long> indices = newsCrawler.fetchArticleIndices(session, page);
            if (indices.isEmpty()) break;

            // 이미 저장된 idxno 제외
            Set<Long> existing = new HashSet<>(newsRepository.findExistingNewsIndexIn(indices));
            List<Long> fresh = indices.stream().filter(idx -> !existing.contains(idx)).toList();
            if (fresh.isEmpty()) {
                log.info("[증분] page={} 신규 없음 - 중단", page);
                break;
            }

            // 신규 idxno만 상세 조회 후 보도/사회만 저장
            List<News> toSave = new ArrayList<>();
            for (Long idx : fresh) {
                newsCrawler.fetchArticle(session, idx).ifPresent(toSave::add);
            }
            if (!toSave.isEmpty()) {
                savedAll.addAll(newsRepository.saveAll(toSave));
            }
            log.info("[증분] page={} 신규 {}건 중 보도/사회 {}건 저장", page, fresh.size(), toSave.size());
        }

        log.info("[증분] 총 {}건 저장", savedAll.size());
        return NewsResponseDTO.fromEntityToList(savedAll);
    }

    /**
     * 백필: 전체 목록을 끝까지 훑어 과거 기사를 모두 수집(보도/사회만).
     * 수천 건 상세 요청이라 오래 걸리므로 비동기로 실행한다.
     */
    @Async
    public void backfillAll() {
        Connection session = newsCrawler.openSession();
        int savedCount = 0;

        log.info("[백필] 시작");
        for (int page = 1; page <= BACKFILL_MAX_PAGES; page++) {
            List<Long> indices = newsCrawler.fetchArticleIndices(session, page);
            if (indices.isEmpty()) {
                log.info("[백필] page={} 빈 페이지 - 종료", page);
                break;
            }

            // 이미 저장된 idxno는 상세 조회 자체를 생략
            Set<Long> existing = new HashSet<>(newsRepository.findExistingNewsIndexIn(indices));
            List<News> toSave = new ArrayList<>();
            for (Long idx : indices) {
                if (existing.contains(idx)) continue;
                newsCrawler.fetchArticle(session, idx).ifPresent(toSave::add);
                sleep(BACKFILL_DELAY_MS);
            }
            if (!toSave.isEmpty()) {
                newsRepository.saveAll(toSave);
                savedCount += toSave.size();
            }
            log.info("[백필] page={} 누적 저장 {}건", page, savedCount);
        }
        log.info("[백필] 완료 - 총 {}건 저장", savedCount);
    }

    /** 카테고리별 또는 전체 삭제 */
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

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
