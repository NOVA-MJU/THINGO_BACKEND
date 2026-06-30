package nova.mjs.domain.thingo.keywordAlarm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [MANUAL] 실제 단말 토큰으로 진짜 푸시를 보낸다(dryRun 아님).
 *
 * 환경변수 FCM_TOKEN 이 있을 때만 실행된다(평소 자동 skip).
 * 실행: (bash)        FCM_TOKEN=토큰값 ./gradlew test --tests "*FcmManualSendIT"
 *      (PowerShell)  $env:FCM_TOKEN="토큰값"; ./gradlew test --tests "*FcmManualSendIT"
 * 성공하면 해당 토큰의 기기/브라우저에 알림이 실제로 뜬다.
 */
@EnabledIfEnvironmentVariable(named = "FCM_TOKEN", matches = ".+")
class FcmManualSendIT {

    private static final String KEY_RESOURCE = "thingo-8eee1-firebase-adminsdk-fbsvc-1f4cb466ac.json";

    @Test
    @DisplayName("[MANUAL] FCM_TOKEN 단말로 실제 푸시 발송")
    void send_real_push_to_token() throws Exception {
        String token = System.getenv("FCM_TOKEN");

        Resource resource = new ClassPathResource(KEY_RESOURCE);
        GoogleCredentials credentials;
        try (InputStream is = resource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(is);
        }
        FirebaseApp app = FirebaseApp.initializeApp(
                FirebaseOptions.builder().setCredentials(credentials).build(),
                "kwalarm-manual-" + System.nanoTime());

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle("'학식' 키워드 새 소식")
                            .setBody("FCM 푸시 실제 발송 테스트입니다")
                            .build())
                    .putData("type", "TEST")
                    .build();

            String messageId = FirebaseMessaging.getInstance(app).send(message); // 실제 발송
            System.out.println("[FCM-MANUAL] sent id=" + messageId);
            assertThat(messageId).isNotBlank();
        } finally {
            app.delete();
        }
    }
}
