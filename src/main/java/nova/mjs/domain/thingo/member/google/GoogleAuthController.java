package nova.mjs.domain.thingo.member.google;

import lombok.RequiredArgsConstructor;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.RefreshTokenCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구글(학교 계정) 로그인·가입. 앱은 네이티브 구글 로그인, 웹은 Google Identity Services로 받은 ID 토큰을 보낸다.
 * 토큰 전달 방식은 이메일 로그인과 같다: 모바일은 본문, 웹은 refreshToken을 HttpOnly 쿠키로
 */
@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
public class GoogleAuthController {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String MOBILE_CLIENT_TYPE = "mobile";

    private final GoogleAuthService googleAuthService;

    @PostMapping
    public ResponseEntity<ApiResponse<GoogleAuthDTO.LoginResult>> login(
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType,
            @Validated @RequestBody GoogleAuthDTO.LoginRequest request
    ) {
        return respond(clientType, googleAuthService.login(request.getIdToken()));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<GoogleAuthDTO.LoginResult>> signup(
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType,
            @Validated @RequestBody GoogleAuthDTO.SignupRequest request
    ) {
        return respond(clientType, googleAuthService.signup(request));
    }

    private ResponseEntity<ApiResponse<GoogleAuthDTO.LoginResult>> respond(String clientType, GoogleAuthDTO.LoginResult result) {
        if (result.getRefreshToken() == null || MOBILE_CLIENT_TYPE.equalsIgnoreCase(clientType)) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        String refreshToken = result.getRefreshToken();
        result.setRefreshToken(null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(refreshToken).toString())
                .body(ApiResponse.success(result));
    }
}
