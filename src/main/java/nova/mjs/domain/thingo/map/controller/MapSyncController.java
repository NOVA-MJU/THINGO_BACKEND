package nova.mjs.domain.thingo.map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSyncDTO;
import nova.mjs.domain.thingo.map.exception.MapSyncException;
import nova.mjs.domain.thingo.map.service.MapSyncService;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.util.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 명지도 동기화 webhook 수신 컨트롤러.
 * 운영팀 구글 시트의 Apps Script가 탭별 행을 모아 POST 한다 (배너 동기화와 동일한 방식).
 *
 * 로그인 유저가 아니므로 JWT/@PreAuthorize 대신 공유 시크릿(X-Sync-Token)으로 인증한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sync/map")
@RequiredArgsConstructor
public class MapSyncController {

    private final MapSyncService mapSyncService;

    // application.yml의 app.sync.map-token으로 주입 (prod에서는 반드시 실제 값으로 덮어쓸 것)
    @Value("${app.sync.map-token:change-me-map-sync-token}")
    private String mapSyncToken;

    @PostMapping
    public ApiResponse<MapSyncDTO.SyncResult> sync(
            @RequestHeader(value = "X-Sync-Token", required = false) String token,
            @RequestBody MapSyncDTO.SyncRequest request
    ) {
        // 1) 공유 시크릿 검증 (상수 시간 비교, 토큰 값은 로그에 남기지 않음)
        validateToken(token);

        // 2) 시트 → DB upsert 동기화
        MapSyncDTO.SyncResult result = mapSyncService.syncFromSheet(request);

        return ApiResponse.success(result);
    }

    private void validateToken(String token) {
        if (token == null || !constantTimeEquals(token, mapSyncToken)) {
            log.warn("[명지도 동기화] 유효하지 않은 토큰으로 동기화 시도가 차단되었습니다.");
            throw new MapSyncException(ErrorCode.MAP_SYNC_UNAUTHORIZED);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
