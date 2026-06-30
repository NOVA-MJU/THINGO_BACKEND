package nova.mjs.domain.thingo.keywordAlarm.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.util.entity.BaseEntity;

/**
 * FCM 기기 토큰.
 *
 * 한 회원이 로그인한 기기의 푸시 토큰. 매칭된 알림을 이 토큰들로 fan-out 한다.
 * 토큰은 기기 단위로 유일(uk_device_token_token): 같은 기기에서 다른 계정으로 로그인하면
 * 토큰 소유자를 새 회원으로 재배정한다(중복 행 방지).
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
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    private DevicePlatform platform;

    @Builder(access = AccessLevel.PRIVATE)
    private DeviceToken(Member member, String fcmToken, DevicePlatform platform) {
        this.member = member;
        this.fcmToken = fcmToken;
        this.platform = platform;
    }

    public static DeviceToken of(Member member, String fcmToken, DevicePlatform platform) {
        return DeviceToken.builder()
                .member(member)
                .fcmToken(fcmToken)
                .platform(platform)
                .build();
    }

    /**
     * 같은 기기 토큰이 다른 계정 로그인으로 재사용될 때 소유 회원을 교체한다.
     */
    public void reassignTo(Member member, DevicePlatform platform) {
        this.member = member;
        this.platform = platform;
    }

    public boolean isOwnedBy(Member member) {
        return this.member != null && this.member.getId() != null
                && this.member.getId().equals(member.getId());
    }
}
