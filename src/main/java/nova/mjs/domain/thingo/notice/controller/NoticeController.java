package nova.mjs.domain.thingo.notice.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.notice.service.NoticeCrawlingService;
import nova.mjs.domain.thingo.notice.service.NoticeService;
import nova.mjs.domain.thingo.notice.dto.NoticeResponseDto;
import nova.mjs.util.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeCrawlingService noticeCrawlingService;
    private final NoticeService noticeService;

    /**
     * 특정 공지 카테고리 크롤링 (운영/관리용)
     *
     * 예시:
     *  - /crawl?type=general
     *  - /crawl?type=law
     */
    @PostMapping("/crawl")
    public void crawlByType(
            @RequestParam("type") String type
    ) {
        noticeCrawlingService.fetchNoticesByType(type);
    }

    /**
     * 전체 공지 크롤링 (모든 카테고리)
     */
    @PostMapping("/crawl/all")
    public void crawlAll() {
        noticeCrawlingService.fetchAllNotices();
    }

    /**
     * DB에 저장된 공지 조회 (사용자 API)
     */
    @GetMapping
    public Page<NoticeResponseDto.Summary> getNotices(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            @RequestParam(value = "sort", defaultValue = "desc") String sort
    ) {
        return noticeService.getNotices(category, year, page, size, sort);
    }

    /**
     * HOT 공지 조회
     *
     * - 프론트에서 page/size 지정 가능
     * - 기본 size 6
     * - 정책: 최근 1개월 이내 공지 중 viewCount DESC, date DESC (서비스/리포지토리 고정)
     */
    @GetMapping("/hot")
    public ResponseEntity<ApiResponse<List<NoticeResponseDto.Summary>>> getHotNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        List<NoticeResponseDto.Summary> response = noticeService.getHotNotices(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}