package nova.mjs.domain.thingo.keywordAlarm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [LIVE] 실제 Google FCM 까지 닿는지 검증.
 *
 * dryRun=true 로 토픽에 검증 발송한다(실제 단말 전달 없음, 기기 토큰 불필요).
 * 성공(메시지 id 반환)이면: 서비스계정 인증 OK + FCM API 활성화 OK + 프로젝트 도달 OK.
 * 외부 네트워크(Google) 호출 + 시크릿 키가 필요하므로 FCM_LIVE=true 일 때만 실행(평소 skip).
 */
@EnabledIfEnvironmentVariable(named = "FCM_LIVE", matches = "true")
class FcmLiveSendIT {

    private static final String KEY_RESOURCE = "thingo-8eee1-firebase-adminsdk-fbsvc-1f4cb466ac.json";

    @Test
    @DisplayName("[LIVE] 서비스계정으로 FCM 에 dryRun 발송이 통과한다")
    void fcm_dryRun_send_reaches_google() throws Exception {
        Resource resource = new ClassPathResource(KEY_RESOURCE);
        assertThat(resource.exists()).isTrue();

        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is);
        }
        FirebaseApp app = FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build(),
                "kwalarm-live-" + System.nanoTime());

        try {
            Message message = Message.builder()
                    .setTopic("kwalarm-healthcheck")
                    .putData("type", "WEEKLY_MENU")
                    .build();

            // dryRun=true -> 실제 전달 없이 FCM 서버가 검증만 수행하고 message id 반환
            String messageId = FirebaseMessaging.getInstance(app).send(message, true);

            System.out.println("[FCM-LIVE] dryRun OK, messageId=" + messageId);
            assertThat(messageId).isNotBlank();
        } finally {
            app.delete();
        }
    }
}
