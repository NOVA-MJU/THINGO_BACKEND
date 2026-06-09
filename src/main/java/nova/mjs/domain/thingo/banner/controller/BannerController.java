package nova.mjs.domain.thingo.banner.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.service.query.BannerQueryService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배너 공개 조회 컨트롤러 (앱).
 */
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerQueryService bannerQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerDTO.Response>>> getBanners(
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(bannerQueryService.getBanners(category))
        );
    }
}
