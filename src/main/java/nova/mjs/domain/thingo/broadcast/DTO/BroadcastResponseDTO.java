package nova.mjs.domain.thingo.broadcast.DTO;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.broadcast.entity.Broadcast;

import java.time.LocalDateTime;

@Getter
@Builder
public class BroadcastResponseDTO {
    private Broadcast.Source source; // BROADCAST(명대 방송국) / OFFICIAL(명지대 공식). 전체 조회 시 항목 구분용
    private String title;
    private String url;
    private String thumbnailUrl;
    private String playlistTitle;
    private LocalDateTime publishedAt;
}