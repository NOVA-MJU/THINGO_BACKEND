package nova.mjs.util.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

//로그인 관련 DTO
public class AuthDTO {

    //로그인 요청 DTO
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LoginRequestDTO{
        private String email;
        private String password;
    }

    //회원가입 시 로그인 response DTO
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoginResponseDTO{
        private String accessToken;
        private String refreshToken;
    }

    // 모바일 로그인 response DTO
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MobileLoginResponseDTO {
        private String accessToken;
        private String refreshToken;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TokenResponseDTO{
        private String accessToken;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RefreshTokenRequestDTO {
        private String refreshToken;
    }
}
