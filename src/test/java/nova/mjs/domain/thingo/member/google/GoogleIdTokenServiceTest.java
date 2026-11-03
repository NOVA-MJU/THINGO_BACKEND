package nova.mjs.domain.thingo.member.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import nova.mjs.util.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenServiceTest {

    private static GoogleIdToken.Payload payload(String email, String hd, Boolean verified) {
        return payload(email, hd, verified, "홍길동");
    }

    private static GoogleIdToken.Payload payload(String email, String hd, Boolean verified, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setHostedDomain(hd);
        payload.setEmailVerified(verified);
        payload.set("name", name);
        payload.set("picture", "https://lh3.googleusercontent.com/a/x");
        return payload;
    }

    @Test
    void 학교_계정이면_소문자_이메일과_이름_사진을_돌려준다() {
        GoogleAccount account = GoogleIdTokenService.toSchoolAccount(payload("Hong@MJU.ac.kr", "mju.ac.kr", true));

        assertThat(account.email()).isEqualTo("hong@mju.ac.kr");
        assertThat(account.name()).isEqualTo("홍길동");
        assertThat(account.pictureUrl()).isEqualTo("https://lh3.googleusercontent.com/a/x");
        assertThat(account.affiliation()).isNull();
    }

    @Test
    void 프로필_이름에_붙은_소속을_이름과_나눈다() {
        // 실제 학교 계정 형식
        assertNameSplit("김현빈 /학생/융합소프트웨어학부 데이터사이언스전공", "김현빈", "학생/융합소프트웨어학부 데이터사이언스전공");
        assertNameSplit("홍길동/융합소프트웨어학부", "홍길동", "융합소프트웨어학부");
        assertNameSplit("홍길동 / 경영학부 경영학전공", "홍길동", "경영학부 경영학전공");
        assertNameSplit("홍길동(자율전공학부(인문))", "홍길동", "자율전공학부(인문))");
        assertNameSplit("홍길동|철학과", "홍길동", "철학과");
        // 소속이 비었거나 이름이 비면 소속 없음, 이름은 메일 아이디로 채운다
        assertNameSplit("홍길동/", "홍길동", null);
        assertNameSplit("/철학과", "hong", "철학과");
        assertNameSplit(null, "hong", null);
        // 영문 이름의 쉼표는 구분자로 보지 않는다
        assertNameSplit("Hong, Gildong", "Hong, Gildong", null);
    }

    @Test
    void 개인_지메일이나_다른_도메인은_거절한다() {
        assertRejected(payload("hong@gmail.com", null, true), ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED);
        assertRejected(payload("hong@mju.ac.kr.evil.com", "evil.com", true), ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED);
        // hd를 흉내 내도 주소가 학교 주소가 아니면 거절
        assertRejected(payload("hong@gmail.com", "mju.ac.kr", true), ErrorCode.GOOGLE_ACCOUNT_NOT_ALLOWED);
    }

    @Test
    void 학교_주소라도_학교가_발급하지_않았거나_인증이_안된_계정은_거절한다() {
        assertRejected(payload("hong@mju.ac.kr", null, true), ErrorCode.GOOGLE_ACCOUNT_NOT_WORKSPACE);
        assertRejected(payload("hong@mju.ac.kr", "mju.ac.kr", false), ErrorCode.GOOGLE_ACCOUNT_NOT_WORKSPACE);
        assertRejected(payload("hong@mju.ac.kr", "mju.ac.kr", null), ErrorCode.GOOGLE_ACCOUNT_NOT_WORKSPACE);
    }

    @Test
    void 클라이언트_ID가_없으면_구글_로그인을_막는다() {
        assertThatThrownBy(() -> new GoogleIdTokenService(List.of()).verify("token"))
                .isInstanceOfSatisfying(GoogleAuthException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_LOGIN_UNAVAILABLE));
    }

    private static void assertNameSplit(String displayName, String name, String affiliation) {
        GoogleAccount account = GoogleIdTokenService.toSchoolAccount(payload("hong@mju.ac.kr", "mju.ac.kr", true, displayName));
        assertThat(account.name()).isEqualTo(name);
        assertThat(account.affiliation()).isEqualTo(affiliation);
    }

    private static void assertRejected(GoogleIdToken.Payload payload, ErrorCode errorCode) {
        assertThatThrownBy(() -> GoogleIdTokenService.toSchoolAccount(payload))
                .isInstanceOfSatisfying(GoogleAuthException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
