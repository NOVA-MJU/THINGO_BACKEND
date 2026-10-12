package nova.mjs.domain.thingo.keywordAlarm.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

import java.util.UUID;

/**
 * FCM 기기 토큰.
 *
 * 기기의 푸시 토큰. 매칭된 알림을 이 토큰들로 fan-out 한다.
 * 토큰은 기기 단위로 유일(uk_device_token_token): 같은 기기에서 다른 계정으로 로그인하면
 * 토큰 소유자를 새 회원으로 재배정한다(중복 행 방지).
 *
 * member 가 null 이면 비로그인(게스트) 토큰이다. 이때 device_id 의 게스트 구독 알림만 받는다.
 * 로그인하면 member 가 채워지고, 로그아웃 후 비로그인 등록이 오면 다시 member=null 로 돌아간다.
 * 로그인 상태의 토큰에도 device_id 를 남겨 두어 "이 토큰이 어느 설치본인지"를 잃지 않는다.
 */
@Entity
@Table(
        name = "device_token",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_device_token_token",
                columnNames = {"fcm_token"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 서버 발급 기기 id (device.device_id). 구버전 앱이 등록한 토큰은 null 일 수 있다. */
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private DevicePlatform platform;

    @Builder(access = AccessLevel.PRIVATE)
    private DeviceToken(Member member, UUID deviceId, String fcmToken, DevicePlatform platform) {
        this.member = member;
        this.deviceId = deviceId;
        this.fcmToken = fcmToken;
        this.platform = platform;
    }

    public static DeviceToken of(Member member, String fcmToken, DevicePlatform platform) {
        return of(member, null, fcmToken, platform);
    }

    public static DeviceToken of(Member member, UUID deviceId, String fcmToken, DevicePlatform platform) {
        return DeviceToken.builder()
                .member(member)
                .deviceId(deviceId)
                .fcmToken(fcmToken)
                .platform(platform)
                .build();
    }

    /** 비로그인 기기의 게스트 토큰 */
    public static DeviceToken ofGuest(UUID deviceId, String fcmToken, DevicePlatform platform) {
        return of(null, deviceId, fcmToken, platform);
    }

    /**
     * 같은 기기 토큰이 다른 계정 로그인으로 재사용될 때 소유 회원을 교체한다.
     * deviceId 가 없으면(헤더 미전송 구버전 앱) 기존 값을 유지한다.
     */
    public void reassignTo(Member member, UUID deviceId, DevicePlatform platform) {
        this.member = member;
        if (deviceId != null) {
            this.deviceId = deviceId;
        }
        this.platform = platform;
    }

    /**
     * 게스트 토큰으로 되돌린다(로그아웃 후 비로그인 등록).
     * 회원 소유를 끊어야 로그아웃한 기기로 회원 알림이 계속 가지 않는다.
     */
    public void detachToGuest(UUID deviceId, DevicePlatform platform) {
        this.member = null;
        this.deviceId = deviceId;
        this.platform = platform;
    }

    public boolean isOwnedBy(Member member) {
        return this.member != null && this.member.getId() != null
                && this.member.getId().equals(member.getId());
    }
}
