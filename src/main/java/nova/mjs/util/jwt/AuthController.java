package nova.mjs.util.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.AuthDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String MOBILE_CLIENT_TYPE = "mobile";

    private final JwtUtil jwtUtil;

    /**
     * Refresh Token 기반 Access Token 재발급 정책:
     *
     * Web:
     * - refreshToken은 HttpOnly Cookie로 전달된다.
     *
     * Mobile:
     * - refreshToken은 JSON body로 전달된다.
     *
     * X-Client-Type은 응답/입력 포맷 분기용이며 권한 판단에는 사용하지 않는다.
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<AuthDTO.TokenResponseDTO>> reissueAccessToken(
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType,
            @CookieValue(value = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) AuthDTO.RefreshTokenRequestDTO request
    ) {
        log.info("토큰 재발급 요청");

        String refreshToken = resolveRefreshToken(clientType, cookieRefreshToken, request);
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Reissue 실패: refreshToken 없음");
            throw new RuntimeException("REFRESH_TOKEN_MISSING");
        }

        AuthDTO.TokenResponseDTO newAccessToken = jwtUtil.reissueToken(refreshToken);

        log.info("Access Token 재발급 성공");
        return ResponseEntity.ok(ApiResponse.success(newAccessToken));
    }

    private String resolveRefreshToken(String clientType,
                                       String cookieRefreshToken,
                                       AuthDTO.RefreshTokenRequestDTO request) {
            if (isMobileClient(clientType)) {
            return request == null ? null : request.getRefreshToken();
        }

        return cookieRefreshToken;
    }

    private boolean isMobileClient(String clientType) {
        return MOBILE_CLIENT_TYPE.equalsIgnoreCase(clientType);
    }
}
