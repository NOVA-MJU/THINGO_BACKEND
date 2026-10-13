package nova.mjs.domain.thingo.member.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.department.entity.enumList.DepartmentName;

public class GoogleAuthDTO {

    public enum Status { LOGGED_IN, SIGNUP_REQUIRED }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "구글 ID 토큰은 필수입니다.")
        private String idToken;
    }

    /**
     * 기존 회원이면 LOGGED_IN과 토큰을, 처음 온 학교 계정이면 SIGNUP_REQUIRED와 가입용 signupToken을 준다.
     * refreshToken은 모바일에만 본문으로 주고, 웹은 이메일 로그인과 같이 HttpOnly 쿠키로 준다
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoginResult {
        private Status status;
        private String accessToken;
        private String refreshToken;
        private String signupToken;
        private String email;
        private String name;
        private String profileImageUrl;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SignupRequest {
        @NotBlank(message = "가입 토큰은 필수입니다.")
        private String signupToken;

        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;

        @NotNull(message = "단과대 정보는 필수입니다.")
        private College college;

        private DepartmentName departmentName;

        @Pattern(regexp = "\\d{8}", message = "학번은 정확히 8자리 숫자여야 합니다.")
        @NotNull(message = "학번은 필수입니다.")
        private String studentNumber;

        private String profileImageUrl;

        @NotNull(message = "개인정보 처리방침 동의 여부는 필수입니다.")
        @AssertTrue(message = "개인정보 처리방침에 동의해야 가입할 수 있습니다.")
        private Boolean privacyAgreed;
    }
}
