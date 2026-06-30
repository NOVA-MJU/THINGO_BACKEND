package nova.mjs.domain.thingo.broadcast.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.broadcast.DTO.BroadcastResponseDTO;
import nova.mjs.domain.thingo.broadcast.service.BroadcastService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/broadcast")
public class BroadcastController {

    private final BroadcastService broadcastService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<String>> syncAll() {
        broadcastService.syncAllByChannelId();
        return ResponseEntity.ok(ApiResponse.success("명지대학교 방송국 전체 영상 동기화 완료"));
    }

    // 명지대학교 공식 유튜브(@mjuniv) 최신 영상만 동기화
    @PostMapping("/official/sync")
    public ResponseEntity<ApiResponse<String>> syncOfficial() {
        broadcastService.syncOfficialLatest();
        return ResponseEntity.ok(ApiResponse.success("명지대학교 공식 유튜브 최신 영상 동기화 완료"));
    }

    /**
     * 영상 목록 조회 (칩: 전체/명지대공식/명대뉴스). 전부 유튜브 콘텐츠.
     * source 미지정 = ALL(방송국+공식). OFFICIAL=명지대 공식, BROADCAST(=NEWS 별칭)=명대 방송국.
     * 모두 최신순(publishedAt DESC).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BroadcastResponseDTO>>> getBroadcasts(
            @RequestParam(defaultValue = "ALL") String source,
            @PageableDefault(size = 9, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BroadcastResponseDTO> result = broadcastService.getVideos(source, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
