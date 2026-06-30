package nova.mjs.domain.thingo.keywordAlarm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FCM 서비스계정 키가 클래스패스에 있고, FirebaseApp 초기화까지 되는지 검증.
 * (네트워크 호출 없음 - 자격증명 파싱 + 앱 초기화만 확인)
 *
 * 시크릿 키(보안 서브모듈)가 있어야 하므로 FCM_LIVE=true 일 때만 실행(키 없는 CI 에선 skip).
 */
@EnabledIfEnvironmentVariable(named = "FCM_LIVE", matches = "true")
class FcmCredentialsTest {

    private static final String KEY_RESOURCE = "thingo-8eee1-firebase-adminsdk-fbsvc-1f4cb466ac.json";

    @Test
    @DisplayName("서비스계정 키가 로드되고 FirebaseApp/Messaging 이 초기화된다")
    void should_load_credentials_and_init_app() throws Exception {
        // given - 보안 서브모듈의 키가 클래스패스 루트에 존재
        Resource resource = new ClassPathResource(KEY_RESOURCE);
        assertThat(resource.exists())
                .as("FCM 키가 클래스패스에 있어야 함 (MJS-BACK-SECURITY resources)")
                .isTrue();

        // when - 자격증명 파싱 + FirebaseApp 초기화(고유 이름으로 격리)
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is);
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options, "kwalarm-test-" + System.nanoTime());

        // then - Messaging 획득 가능
        try {
            assertThat(FirebaseMessaging.getInstance(app)).isNotNull();
        } finally {
            app.delete();
        }
    }
}
