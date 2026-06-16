package nova.mjs.domain.thingo.news.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.news.DTO.NewsResponseDTO;
import nova.mjs.domain.thingo.news.service.NewsService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {
    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NewsResponseDTO>>> getNews(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "date")
                        .and(Sort.by(Sort.Direction.DESC, "newsIndex"))
        );
        Page<NewsResponseDTO> newsPage = newsService.getNewsByCategory(category, pageable);
        return ResponseEntity.ok(ApiResponse.success(newsPage));
    }


    // 증분 크롤링: 최신 신규 기사(보도/사회)만 저장
    @PostMapping
    public ResponseEntity<ApiResponse<List<NewsResponseDTO>>> crawlLatest() {
        List<NewsResponseDTO> savedNews = newsService.crawlLatest();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(savedNews));
    }

    // 백필: 과거 기사 전체(보도/사회)를 비동기로 수집. 즉시 202 반환 후 백그라운드 실행.
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<String>> backfill() {
        newsService.backfillAll();
        return ResponseEntity
                .accepted()
                .body(ApiResponse.success("백필을 시작했습니다. 진행 상황은 서버 로그를 확인하세요."));
    }

    @DeleteMapping
    public void deleteAllNews(@RequestParam(required = false) String category) {
        newsService.deleteAllNews(category);
    }
}
