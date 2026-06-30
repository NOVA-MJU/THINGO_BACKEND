package nova.mjs.domain.thingo.keywordAlarm.service.fcm;

/**
 * FCM 푸시 발송 추상화.
 * 구현체는 비동기로 발송하며, 매칭 트랜잭션 밖에서 호출된다.
 */
public interface FcmSender {

    /**
     * 한 발송 단위의 모든 기기 토큰으로 푸시를 보낸다.
     * 무효/만료 토큰은 구현체가 정리한다.
     */
    void sendAll(FcmDispatch dispatch);
}
