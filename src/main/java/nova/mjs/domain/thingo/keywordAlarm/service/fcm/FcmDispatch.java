package nova.mjs.domain.thingo.keywordAlarm.service.fcm;

import java.util.Map;
import java.util.List;

/**
 * FCM 발송 단위.
 * 한 회원의 기기 토큰 묶음과 알림 표시/데이터 페이로드.
 */
public record FcmDispatch(
        List<String> tokens,
        String title,
        String body,
        Map<String, String> data
) {
}
