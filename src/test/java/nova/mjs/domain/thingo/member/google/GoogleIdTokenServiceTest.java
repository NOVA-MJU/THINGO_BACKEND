package nova.mjs.domain.thingo.member.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import nova.mjs.util.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenServiceTest {

    private static GoogleIdToken.Payload payload(String email, String hd, Boolean verified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setHostedDomain(hd);
        payload.setEmailVerified(verified);
        payload.set("name", "홍길동");
        payload.set("picture", "https://lh3.googleusercontent.com/a/x");
        return payload;
    }

    @Test
    void 학교_계정이면_소문자_이메일과_이름_사진을_돌려준다() {
        GoogleAccount account = GoogleIdTokenService.toSchoolAccount(payload("Hong@MJU.ac.kr", "mju.ac.kr", true));

        assertThat(account.email()).isEqualTo("hong@mju.ac.kr");
        assertThat(account.name()).isEqualTo("홍길동");
        assertThat(account.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/x");
    }

    @Test
    void 개인_지메일이나_다른_도메인은_거절한다() {
        assertNotAllowed(payload("hong@gmail.com", null, true));
        assertNotAllowed(payload("hong@mju.ac.kr.evil.com", "evil.com", true));
        // hd를 흉내 내도 주소가 학교 주소가 아니면 거절
        assertNotAllowed(payload("hong@gmail.com", "mju.ac.kr", true));
    }

    @Test
    void 이메일_인증이_안된_계정은_거절한다() {
        assertNotAllowed(payload("hong@mju.ac.kr", "mju.ac.kr", false));
        assertNotAllowed(payload("hong@mju.ac.kr", "mju.ac.kr", null));
    }

    @Test
    void 클라이언트_ID가_없으면_구글_로그인을_막는다() {
        assertThatThrownBy(() -> new GoogleIdTokenService(List.of()).verify("token"))
                .isInstanceOfSatisfying(GoogleAuthException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_LOGIN_UNAVAILABLE));
    }

    private static void assertNotAllowed(GoogleIdToken.Payload payload) {
        assertThatThrownBy(() -> GoogleIdTokenService.toSchoolAccount(payload))
                .isInstanceOfSatisfying(GoogleAuthException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED));
    }
}
