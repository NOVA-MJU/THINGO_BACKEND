package nova.mjs.domain.thingo.member.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 앱(네이티브 구글 로그인)과 웹(Google Identity Services)이 보낸 ID 토큰을 검증하고 학교 계정만 통과시킨다.
 * 서명·만료·발급자·대상(우리 웹 클라이언트 ID)은 GoogleIdTokenVerifier가, 학교 계정 여부는 여기서 확인한다
 */
@Slf4j
@Component
public class GoogleIdTokenService {

    static final String SCHOOL_DOMAIN = "mju.ac.kr";

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenService(@Value("${google.oauth.client-ids:}") List<String> clientIds) {
        List<String> audiences = clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        this.verifier = audiences.isEmpty()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(audiences)
                        .build();
    }

    public GoogleAccount verify(String idToken) {
        if (verifier == null) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_LOGIN_UNAVAILABLE);
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("[구글 로그인] ID 토큰 검증 실패: {}", e.getMessage());
            throw new GoogleAuthException(ErrorCode.GOOGLE_ID_TOKEN_INVALID, e);
        }
        if (token == null) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_ID_TOKEN_INVALID);
        }
        return toSchoolAccount(token.getPayload());
    }

    /**
     * 학교 Google Workspace 계정(hd=mju.ac.kr)이면서 이메일이 인증됐고 @mju.ac.kr로 끝나야 한다.
     * hd만으로도 충분하지만, 이메일 주소를 회원 식별자로 쓰므로 주소 형태도 함께 확인한다
     */
    static GoogleAccount toSchoolAccount(GoogleIdToken.Payload payload) {
        String email = payload.getEmail() == null ? "" : payload.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean schoolAccount = SCHOOL_DOMAIN.equalsIgnoreCase(payload.getHostedDomain())
                && Boolean.TRUE.equals(payload.getEmailVerified())
                && email.endsWith("@" + SCHOOL_DOMAIN);
        if (!schoolAccount) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED);
        }
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return new GoogleAccount(email, name.trim(), (String) payload.get("picture"));
    }
}
