package nova.mjs.domain.thingo.broadcast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "broadcast")
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String videoId;

    private String title;

    private String url;

    private String thumbnailUrl;

    private String playlistTitle; // 재생목록

    private LocalDateTime publishedAt;

    /**
     * 영상 출처 구분: 방송국(BROADCAST) / 명지대 공식 유튜브(OFFICIAL).
     * 기존 적재분은 모두 방송국이므로 컬럼 DEFAULT 'BROADCAST' 로 기존 행이 자동 백필된다.
     * 신규 insert 는 항상 Builder 로 source 를 명시하므로 default 에 의존하지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false,
            columnDefinition = "varchar(20) default 'BROADCAST' not null")
    private Source source;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastSyncedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void syncFromYoutube(
            String title,
            String thumbnailUrl,
            LocalDateTime publishedAt,
            String playlistTitle,
            LocalDateTime syncTime
    ) {
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.publishedAt = publishedAt;

        if (playlistTitle != null && !playlistTitle.isBlank()) {
            this.playlistTitle = playlistTitle;
        }

        this.lastSyncedAt = syncTime;
    }

    public enum Source {
        BROADCAST, // 명지대학교 방송국
        OFFICIAL   // 명지대학교 공식 유튜브
    }
}
