package nova.mjs.domain.thingo.keywordAlarm.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase(FCM) 설정.
 *
 * fcm.enabled=true 일 때만 FirebaseApp 을 초기화한다.
 * 자격증명(서비스 계정 JSON)은 fcm.credentials-path 로 주입한다(코드/공개 리포지토리에 하드코딩 금지).
 *  - 보안 서브모듈(MJS-BACK-SECURITY)이 resources 루트라 'classpath:파일명' 으로 로드 가능.
 *  - 'file:/...' 절대경로나 환경 기본 자격증명(미지정 시)도 지원.
 * 미설정(로컬/테스트)이면 빈을 만들지 않아 앱은 정상 부팅하고, FcmSenderImpl 은 발송을 생략한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(@Value("${fcm.credentials-path:}") String credentialsPath) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = loadCredentials(credentialsPath);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("[FCM] FirebaseApp 초기화 완료 (source={})",
                credentialsPath == null || credentialsPath.isBlank() ? "application-default" : credentialsPath);
        return app;
    }

    private GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
        // 경로 미지정 시 환경 기본 자격증명(GOOGLE_APPLICATION_CREDENTIALS 등)
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return GoogleCredentials.getApplicationDefault();
        }
        // classpath:/file:/그 외 경로를 ResourceLoader 로 일관 처리
        String location = credentialsPath.contains(":") ? credentialsPath : "file:" + credentialsPath;
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("[FCM] 자격증명 파일을 찾을 수 없습니다: " + location);
        }
        try (InputStream is = resource.getInputStream()) {
            return GoogleCredentials.fromStream(is);
        }
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
