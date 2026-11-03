package nova.mjs.domain.thingo.member.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 앱(네이티브 구글 로그인)과 웹(Google Identity Services)이 보낸 ID 토큰을 검증하고 학교 계정만 통과시킨다.
 * 서명·만료·발급자·대상(우리 웹 클라이언트 ID)은 GoogleIdTokenVerifier가, 학교 계정 여부는 여기서 확인한다
 */
@Slf4j
@Component
public class GoogleIdTokenService {

    static final String SCHOOL_DOMAIN = "mju.ac.kr";

    // 학교 계정 프로필 이름은 "김현빈 /학생/융합소프트웨어학부 데이터사이언스전공" 형식이다. 첫 구분자 앞은 이름, 뒤는 소속으로 나눈다.
    // 쉼표·공백은 영문 이름("Hong, Gildong")에도 쓰여 이름이 잘릴 수 있으므로 구분자로 쓰지 않는다
    private static final Pattern NAME_SEPARATOR = Pattern.compile("[/|(]");

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
        } catch (IOException e) {
            // 구글 공개키를 받아 오지 못한 경우라 사용자의 토큰 문제가 아니다
            log.error("[구글 로그인] 구글 공개키 조회 실패: {}", e.getMessage());
            throw new GoogleAuthException(ErrorCode.GOOGLE_LOGIN_UNAVAILABLE, e);
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
        if (!email.endsWith("@" + SCHOOL_DOMAIN)) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED);
        }
        // 학교 주소로 직접 만든 개인 구글 계정은 hd가 없다. 학교가 발급한 계정인지 따로 알려 줘야 사용자가 계정을 바꿔 고른다
        if (!SCHOOL_DOMAIN.equalsIgnoreCase(payload.getHostedDomain()) || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_ACCOUNT_NOT_WORKSPACE);
        }

        String displayName = payload.get("name") instanceof String value ? value.trim() : "";
        Matcher separator = NAME_SEPARATOR.matcher(displayName);
        String name = displayName;
        String affiliation = null;
        if (separator.find()) {
            name = displayName.substring(0, separator.start()).trim();
            affiliation = displayName.substring(separator.end()).trim();
        }
        if (name.isEmpty()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return new GoogleAccount(email, name, (String) payload.get("picture"),
                affiliation == null || affiliation.isEmpty() ? null : affiliation);
    }
}
