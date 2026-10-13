package nova.mjs.util.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/** 웹 로그인 응답에 싣는 refreshToken 쿠키. 이메일 로그인과 구글 로그인이 같은 속성을 써야 재발급이 같은 쿠키를 읽는다 */
public final class RefreshTokenCookie {

    private RefreshTokenCookie() {
    }

    public static ResponseCookie of(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();
    }
}
