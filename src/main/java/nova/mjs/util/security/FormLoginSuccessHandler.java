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
import org.springframework.http.ResponseCookie;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormLoginSuccessHandler implements AuthenticationSuccessHandler {//로그인 성공 시 jwt 생성, 응답 설정

    //로그인 성공 시 자동으로 호출되는 handler
    //filter 부분에서 인증 성공 후 jwt 생성하고 응답하는 역할을 구현했었음
    //filter만 사용 - 필터에서 인증 flow 관리하므로 security 인증 절차에 통합, 성공시 contextHolder에 자동 저장
    //대신 시간이나 로그를 기록하지 못함
    //handler - 성공 이벤트에서 실행, 인증과 인증 성공 후 작업이 분리 -> 재사용 가능 / 기록 저장, 보안 로깅 추가 용이

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("로그인 성공 {}", authentication.getName());

        //인증된 사용자 정보 가져오기
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID uuid = principal.getUuid();
        String email = principal.getEmail(); //SecurityContext에서 username(email) 가져오기
        //String email = authentication.getName(); 이것도 맞을 수 있음. principal에서 getUserName을 email로 반환하기 때문
        String role = principal.getRole();


        //JWT 생성
        String accessToken = jwtUtil.generateAccessToken(uuid, email, role);
        String refreshToken = jwtUtil.generateRefreshToken(uuid, email, role);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)              // HTTPS 사용 환경
                .sameSite("None")          // 크로스사이트 요청 시 필요
                .path("/")                 // 전역 경로
                .maxAge(Duration.ofDays(14)) // 정책에 맞게 조정 (JwtUtil의 만료와 일치 권장)
                // .domain(".your-domain.com") // 필요 시 설정
                .build();
        response.addHeader("Set-Cookie", refreshCookie.toString());

        //응답 json 생성 - 데이터 준비
        AuthDTO.LoginResponseDTO responseData = AuthDTO.LoginResponseDTO.builder()
                .accessToken(accessToken)
                .build();

        // 실제 응답이 정상적으로 반환되는지 확인
        log.info("로그인 응답 데이터: {}", responseData);

        //json 응답 설정
        
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(responseData));
        response.getWriter().flush();


        log.info("JWT 응답 전송 완료");
    }
}
