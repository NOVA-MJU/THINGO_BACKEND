package nova.mjs.domain.thingo.keywordAlarm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 비로그인 사용자의 앱 설치 단위 식별자.
 *
 * 로그인 없이도 키워드 알림을 받을 수 있도록, 구독/알림함/FCM 토큰을 회원 대신 이 기기에 묶는다.
 * id 는 서버가 발급한다(UUID.randomUUID = SecureRandom). 클라이언트가 만든 값을 받으면
 * 추측 가능한 id 로 남의 알림함을 열 수 있으므로, 여기 등록된 id 만 X-Device-Id 로 인정한다.
 */
@Entity
@Table(name = "device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    /** 마지막 사용 시각은 이 간격보다 오래됐을 때만 갱신한다(요청마다 UPDATE 하지 않도록). */
    private static final Duration LAST_SEEN_RESOLUTION = Duration.ofDays(1);

    @Id
    @Column(name = "device_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 장기 미사용 기기 정리 판단용 */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    private Device(UUID id, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public static Device issue() {
        return new Device(UUID.randomUUID(), Instant.now());
    }

    public void touch(Instant now) {
        if (lastSeenAt.plus(LAST_SEEN_RESOLUTION).isBefore(now)) {
            this.lastSeenAt = now;
        }
    }
}
