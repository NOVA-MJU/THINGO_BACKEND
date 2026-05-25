package nova.mjs.domain.thingo.community.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.config.aop.LogExecutionTime;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardRequest;
import nova.mjs.domain.thingo.community.DTO.CommunityBoardResponse;
import nova.mjs.domain.thingo.community.service.CommunityBoardServiceImpl;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.s3.S3ServiceImpl;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class CommunityBoardController {

    private final CommunityBoardServiceImpl communityBoardServiceImpl;
    private final S3ServiceImpl s3ServiceImpl;

    private static final List<String> ALLOWED_SORT_FIELDS =
            Arrays.asList("createdAt", "title", "likeCount", "viewCount");

    /**
     * 1. 게시글 목록 조회 (페이지네이션 + 정렬 + 카테고리)
     *
     * @param communityCategory 카테고리 문자열
     *                          - "ALL": 전체
     *                          - "FREE", "QNA", ... : enum CommunityCategory 값
     */
    @GetMapping
    @LogExecutionTime("게시글 페이지네이션 목록 조회")
    public ResponseEntity<ApiResponse<Page<CommunityBoardResponse.SummaryDTO>>> getBoards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String communityCategory,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;

        // 잘못된 sortBy 값 방어
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            sortDirection = Sort.Direction.DESC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<CommunityBoardResponse.SummaryDTO> boards =
                communityBoardServiceImpl.getBoards(pageable, email, communityCategory);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(boards));
    }

    /**
     * 2. 게시글 상세 조회
     */
    @GetMapping("/{boardUuid}")
    public ResponseEntity<ApiResponse<CommunityBoardResponse.DetailDTO>> getBoardDetail(
            @PathVariable UUID boardUuid,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;

        CommunityBoardResponse.DetailDTO board =
                communityBoardServiceImpl.getBoardDetail(boardUuid, email);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(board));
    }

    /**
     * 3. 게시글 작성
     *
     * body 예:
     * {
     *   "title": "제목",
     *   "content": "<p>본문</p>",
     *   "contentPreview": "요약...",
     *   "published": true,
     *   "communityCategory": "FREE"
     * }
     */
    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityBoardResponse.DetailDTO>> createBoard(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CommunityBoardRequest request
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;

        CommunityBoardResponse.DetailDTO board =
                communityBoardServiceImpl.createBoard(request, email);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(board));
    }

    /**
     * 4. 게시글 수정
     */
    @PatchMapping("/{boardUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommunityBoardResponse.DetailDTO>> updateBoard(
            @PathVariable UUID boardUuid,
            @RequestBody CommunityBoardRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;

        CommunityBoardResponse.DetailDTO board =
                communityBoardServiceImpl.updateBoard(boardUuid, request, email);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(board));
    }

    /**
     * 5. 게시글 삭제
     */
    @DeleteMapping("/{boardUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBoard(
            @PathVariable UUID boardUuid,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        String email = (userPrincipal != null) ? userPrincipal.getUsername() : null;

        communityBoardServiceImpl.deleteBoard(boardUuid, email);

        return ResponseEntity.noContent().build();
    }

    /**
     * 6. 핫 게시글 조회
     */
    @GetMapping("/hot")
    public ApiResponse<List<CommunityBoardResponse.SummaryDTO>> getHotBoards() {
        List<CommunityBoardResponse.SummaryDTO> response = communityBoardServiceImpl.getHotBoards();
        return ApiResponse.success(response);
    }

}
