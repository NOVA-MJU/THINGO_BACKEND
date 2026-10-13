package nova.mjs.util.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.util.jwt.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String MOBILE_CLIENT_TYPE = "mobile";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("로그인 성공 {}", authentication.getName());

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID uuid = principal.getUuid();
        String email = principal.getEmail();
        String role = principal.getRole();

        String accessToken = jwtUtil.generateAccessToken(uuid, email, role);
        String refreshToken = jwtUtil.generateRefreshToken(uuid, email, role);

        if (isMobileRequest(request)) {
            writeMobileResponse(response, accessToken, refreshToken);
        } else {
            writeWebResponse(response, accessToken, refreshToken);
        }

        log.info("JWT 응답 전송 완료");
    }

    private boolean isMobileRequest(HttpServletRequest request) {
        return MOBILE_CLIENT_TYPE.equalsIgnoreCase(request.getHeader(CLIENT_TYPE_HEADER));
    }

    private void writeWebResponse(HttpServletResponse response,
                                  String accessToken,
                                  String refreshToken) throws IOException {
        response.addHeader("Set-Cookie", RefreshTokenCookie.of(refreshToken).toString());

        AuthDTO.LoginResponseDTO responseData = AuthDTO.LoginResponseDTO.builder()
                .accessToken(accessToken)
                .build();

        writeJsonResponse(response, responseData);
    }

    private void writeMobileResponse(HttpServletResponse response,
                                     String accessToken,
                                     String refreshToken) throws IOException {
        AuthDTO.MobileLoginResponseDTO responseData = AuthDTO.MobileLoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();

        writeJsonResponse(response, responseData);
    }

    private void writeJsonResponse(HttpServletResponse response, Object responseData) throws IOException {
        log.info("로그인 응답 데이터 {}", responseData);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(responseData));
        response.getWriter().flush();
    }
}
