package nova.mjs.domain.thingo.keywordAlarm.entity;

import nova.mjs.domain.thingo.member.entity.Member;

import java.util.UUID;

/**
 * 키워드 알림 구독/알림함의 소유자이자 발송 수신자.
 *
 * 로그인 회원 또는 비로그인 기기(Device) 중 정확히 하나다.
 * DB 의 keyword_subscription / notification_history / device_token 도 같은 규칙
 * (member_id, device_id 중 하나만 채움)을 따른다.
 */
public record AlarmOwner(Member member, UUID deviceId) {

    public AlarmOwner {
        if ((member == null) == (deviceId == null)) {
            throw new IllegalArgumentException("알림 소유자는 회원 또는 기기 중 정확히 하나여야 합니다.");
        }
    }

    public static AlarmOwner ofMember(Member member) {
        return new AlarmOwner(member, null);
    }

    public static AlarmOwner ofDevice(UUID deviceId) {
        return new AlarmOwner(null, deviceId);
    }

    public boolean isMember() {
        return member != null;
    }

    /**
     * 수신자 단위 묶음/중복 차단(Redis dedup) 키.
     * 회원은 기존 키 형식(회원 id 그대로)을 유지해 배포 전후 dedup 이 이어지게 하고,
     * 기기는 "d:" 접두로 회원 id 와 겹치지 않게 한다.
     */
    public String key() {
        return isMember() ? key(member.getId(), null) : key(null, deviceId);
    }

    /** 엔티티를 읽지 않고 id 만으로 수신자 키를 만든다(매칭 결과 묶기용). */
    public static String key(Long memberId, UUID deviceId) {
        return memberId != null ? String.valueOf(memberId) : "d:" + deviceId;
    }

    @Override
    public String toString() {
        return isMember() ? "member:" + member.getId() : "device:" + deviceId;
    }
}
