package nova.mjs.domain.thingo.banner.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.exception.BannerSyncException;
import nova.mjs.domain.thingo.banner.service.command.BannerSyncService;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 배너 동기화 webhook 수신 컨트롤러.
 * 운영팀 구글 시트의 Apps Script(onChange)가 시트 전체를 POST한다.
 *
 * 로그인 유저가 아니므로 JWT/@PreAuthorize 대신 공유 시크릿(X-Sync-Token)으로 인증한다.
 */
@RestController
@RequestMapping("/api/v1/sync/banners")
@RequiredArgsConstructor
@Log4j2
public class BannerSyncController {

    private final BannerSyncService bannerSyncService;

    // 서버 환경변수 BANNER_SYNC_TOKEN으로 주입 (미설정 시 기본값 - prod에서는 반드시 env로 덮어쓸 것)
    @Value("${BANNER_SYNC_TOKEN:change-me-banner-sync-token}")
    private String bannerSyncToken;

    @PostMapping
    public ResponseEntity<ApiResponse<BannerDTO.SyncResult>> sync(
            @RequestHeader(value = "X-Sync-Token", required = false) String token,
            @RequestBody @Valid BannerDTO.SyncRequest request
    ) {
        // 1) 공유 시크릿 검증 (상수 시간 비교, 토큰 값은 로그에 남기지 않음)
        validateToken(token);

        // 2) 전체 교체 동기화
        BannerDTO.SyncResult result = bannerSyncService.syncFromSheet(request);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private void validateToken(String token) {
        if (token == null || !constantTimeEquals(token, bannerSyncToken)) {
            log.warn("[배너 동기화] 유효하지 않은 토큰으로 동기화 시도가 차단되었습니다.");
            throw new BannerSyncException(ErrorCode.BANNER_SYNC_UNAUTHORIZED);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
