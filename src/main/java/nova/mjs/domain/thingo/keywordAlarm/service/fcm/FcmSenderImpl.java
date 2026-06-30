package nova.mjs.domain.thingo.keywordAlarm.service.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.service.DeviceTokenService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * FCM 발송 구현.
 *
 * - 비동기(@Async)로 발송해 매칭/색인 흐름을 막지 않는다.
 * - FirebaseMessaging 빈이 없으면(자격증명 미설정) 발송을 생략한다.
 * - 무효/만료 토큰(UNREGISTERED/INVALID_ARGUMENT)은 즉시 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSenderImpl implements FcmSender {

    /** FirebaseMessaging 빈이 없을 수 있어(로컬/테스트) ObjectProvider 로 선택 주입 */
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final DeviceTokenService deviceTokenService;

    @Async
    @Override
    public void sendAll(FcmDispatch dispatch) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.info("[FCM] 비활성(자격증명 미설정) - 발송 생략. tokenCount={}", dispatch.tokens().size());
            return;
        }

        // 알림 표시: 제목=등록 키워드, 본문=콘텐츠 제목
        Notification notification = Notification.builder()
                .setTitle("'" + dispatch.title() + "' 키워드 새 소식")
                .setBody(dispatch.body())
                .build();

        for (String token : dispatch.tokens()) {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(notification)
                    .putAllData(dispatch.data())
                    .build();
            try {
                messaging.send(message);
            } catch (FirebaseMessagingException e) {
                handleSendFailure(token, e);
            }
        }
    }

    /**
     * 발송 실패 처리. 무효/만료 토큰은 정리하고, 일시 오류는 로그만 남긴다.
     */
    private void handleSendFailure(String token, FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
            log.info("[FCM] 무효 토큰 정리 - code={}", code);
            deviceTokenService.deleteByToken(token);
        } else {
            log.warn("[FCM] 발송 실패(유지) - code={}, msg={}", code, e.getMessage());
        }
    }
}
