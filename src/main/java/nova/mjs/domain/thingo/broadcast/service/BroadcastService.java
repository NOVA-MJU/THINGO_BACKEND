package nova.mjs.domain.thingo.broadcast.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.broadcast.DTO.BroadcastResponseDTO;
import nova.mjs.domain.thingo.broadcast.entity.Broadcast;
import nova.mjs.domain.thingo.broadcast.exception.BroadcastSyncException;
import nova.mjs.domain.thingo.broadcast.repository.BroadcastRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class BroadcastService {
    private final BroadcastRepository broadcastRepository;

    // 영상 칩(source) 값. 모두 유튜브 콘텐츠. (명대뉴스 = 명대 방송국 채널)
    private static final String SOURCE_ALL = "ALL";            // 방송국 + 공식
    private static final String SOURCE_OFFICIAL = "OFFICIAL";   // 명지대 공식(@mjuniv)
    private static final String SOURCE_BROADCAST = "BROADCAST"; // 명대 방송국
    private static final String SOURCE_NEWS = "NEWS";           // 명대 방송국 별칭(기존 칩명 '명대뉴스')

    @Value("${youtube.api.key}")
    private String apiKey;

    @Value("${youtube.channel.id}")
    private String channelId;

    // 명지대학교 공식 유튜브 핸들(@mjuniv). 채널ID 대신 forHandle 로 런타임 해석한다.
    @Value("${youtube.official.handle:@mjuniv}")
    private String officialHandle;

    // 공식 채널은 "최신만" 노출 정책이라 업로드 목록 상단 N개만 동기화한다.
    private static final int OFFICIAL_LATEST_COUNT = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient youtubeClient;

    public BroadcastService(
            BroadcastRepository broadcastRepository,
            @Value("${youtube.api.key}") String apiKey,
            @Value("${youtube.channel.id}") String channelId,
            @Value("${youtube.official.handle:@mjuniv}") String officialHandle,
            @Qualifier("youtubeApiClient") WebClient youtubeClient
    ) {
        this.broadcastRepository = broadcastRepository;
        this.apiKey = apiKey;
        this.channelId = channelId;
        this.officialHandle = officialHandle;
        this.youtubeClient = youtubeClient;
    }

    @Transactional
    public void syncAllByChannelId() {
        final LocalDateTime syncTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        final LocalDateTime cutoff = syncTime.minusYears(3);

        try {
            syncAllPlaylists(syncTime, cutoff);
            syncUploadedVideos(syncTime, cutoff);

            // 방송국(BROADCAST) 출처만 정리. 공식(OFFICIAL) 데이터는 건드리지 않는다.
            broadcastRepository.deleteBySourceAndPublishedAtBefore(Broadcast.Source.BROADCAST, cutoff);
            broadcastRepository.deleteBySourceAndLastSyncedAtBefore(Broadcast.Source.BROADCAST, syncTime);

        } catch (Exception e) {
            throw new BroadcastSyncException();
        }
    }

    /**
     * 명지대학교 공식 유튜브(@mjuniv) 최신 영상 동기화.
     * 1) 핸들 -> 채널 -> 업로드 재생목록ID 해석(forHandle)
     * 2) 업로드 목록 상단(최신) N개만 upsert (OFFICIAL)
     * 3) 이번 동기화에 포함되지 않은 OFFICIAL 행 삭제 -> 항상 "최신 N개"만 유지
     */
    @Transactional
    public void syncOfficialLatest() {
        final LocalDateTime syncTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        try {
            String uploadsPlaylistId = resolveUploadsPlaylistByHandle(officialHandle);
            if (uploadsPlaylistId == null || uploadsPlaylistId.isBlank()) {
                throw new BroadcastSyncException();
            }

            syncLatestFromPlaylist(uploadsPlaylistId, syncTime, OFFICIAL_LATEST_COUNT);

            // 최신 N개 밖으로 밀려난 과거 OFFICIAL 행 제거
            broadcastRepository.deleteBySourceAndLastSyncedAtBefore(Broadcast.Source.OFFICIAL, syncTime);

        } catch (Exception e) {
            throw new BroadcastSyncException();
        }
    }

    // 유튜브 핸들(@mjuniv)로 채널의 업로드 재생목록ID 를 조회한다.
    private String resolveUploadsPlaylistByHandle(String handle) throws Exception {
        String normalized = handle.startsWith("@") ? handle.substring(1) : handle;
        String url = "/channels?part=contentDetails&forHandle=" + normalized + "&key=" + apiKey;

        String json = youtubeClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return objectMapper.readTree(json)
                .path("items").path(0)
                .path("contentDetails")
                .path("relatedPlaylists")
                .path("uploads")
                .asText(null);
    }

    // 업로드 재생목록 상단(최신) count 개를 한 페이지로 가져와 OFFICIAL 로 upsert.
    private void syncLatestFromPlaylist(String playlistId, LocalDateTime syncTime, int count) throws Exception {
        int max = Math.min(count, 50); // playlistItems 단일 페이지 상한 50

        String url = new StringBuilder("/playlistItems")
                .append("?part=snippet,contentDetails")
                .append("&maxResults=").append(max)
                .append("&playlistId=").append(playlistId)
                .append("&key=").append(apiKey)
                .toString();

        String json = youtubeClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(json);

        for (JsonNode item : root.path("items")) {
            JsonNode snippet = item.path("snippet");
            JsonNode contentDetails = item.path("contentDetails");

            String videoId = snippet.path("resourceId").path("videoId").asText(null);
            if (videoId == null || videoId.isBlank()) continue;

            String title = snippet.path("title").asText("");
            String thumbnail = snippet.path("thumbnails").path("high").path("url").asText("");

            String publishedAtStr = contentDetails.path("videoPublishedAt").asText(null);
            if (publishedAtStr == null || publishedAtStr.isBlank()) {
                publishedAtStr = snippet.path("publishedAt").asText(null);
            }
            if (publishedAtStr == null || publishedAtStr.isBlank()) continue;

            LocalDateTime publishedAt = parseYoutubeDateTime(publishedAtStr);

            // 공식 채널은 재생목록 개념 없이 단일 피드로 노출하므로 playlistTitle = null
            upsertBroadcast(videoId, title, thumbnail, publishedAt, null, syncTime, Broadcast.Source.OFFICIAL);
        }
    }

    private void syncAllPlaylists(LocalDateTime syncTime, LocalDateTime cutoff) throws Exception {
        String pageToken = "";

        while (pageToken != null) {
            StringBuilder uriBuilder = new StringBuilder("/playlists")
                    .append("?part=snippet")
                    .append("&maxResults=50")
                    .append("&channelId=").append(channelId)
                    .append("&key=").append(apiKey);

            if (!pageToken.isEmpty()) {
                uriBuilder.append("&pageToken=").append(pageToken);
            }

            String json = youtubeClient.get()
                    .uri(uriBuilder.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            pageToken = root.path("nextPageToken").asText(null);

            for (JsonNode item : root.path("items")) {
                String playlistId = item.path("id").asText(null);
                if (playlistId == null || playlistId.isBlank()) continue;

                String playlistTitle = item.path("snippet").path("title").asText(null);
                syncPlaylistItems(playlistId, playlistTitle, syncTime, cutoff);
            }
        }
    }

    private void syncPlaylistItems(
            String playlistId,
            String playlistTitle,
            LocalDateTime syncTime,
            LocalDateTime cutoff
    ) throws Exception {

        String pageToken = "";

        while (pageToken != null) {
            StringBuilder uriBuilder = new StringBuilder("/playlistItems")
                    .append("?part=snippet,contentDetails")
                    .append("&maxResults=50")
                    .append("&playlistId=").append(playlistId)
                    .append("&key=").append(apiKey);

            if (!pageToken.isEmpty()) {
                uriBuilder.append("&pageToken=").append(pageToken);
            }

            String json = youtubeClient.get()
                    .uri(uriBuilder.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            pageToken = root.path("nextPageToken").asText(null);

            for (JsonNode item : root.path("items")) {
                JsonNode snippet = item.path("snippet");
                JsonNode contentDetails = item.path("contentDetails");

                String videoId = snippet.path("resourceId").path("videoId").asText(null);
                if (videoId == null || videoId.isBlank()) continue;

                String title = snippet.path("title").asText("");
                String thumbnail = snippet.path("thumbnails").path("high").path("url").asText("");

                String publishedAtStr = contentDetails.path("videoPublishedAt").asText(null);
                if (publishedAtStr == null || publishedAtStr.isBlank()) {
                    publishedAtStr = snippet.path("publishedAt").asText(null);
                }
                if (publishedAtStr == null || publishedAtStr.isBlank()) continue;

                LocalDateTime publishedAt = parseYoutubeDateTime(publishedAtStr);

                if (publishedAt.isBefore(cutoff)) continue;

                upsertBroadcast(videoId, title, thumbnail, publishedAt, playlistTitle, syncTime, Broadcast.Source.BROADCAST);
            }
        }
    }

    private void syncUploadedVideos(LocalDateTime syncTime, LocalDateTime cutoff) throws Exception {
        String url = "/channels?part=contentDetails&id=" + channelId + "&key=" + apiKey;

        String json = youtubeClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String uploadsId = objectMapper.readTree(json)
                .path("items").path(0)
                .path("contentDetails")
                .path("relatedPlaylists")
                .path("uploads")
                .asText(null);

        if (uploadsId == null || uploadsId.isBlank()) return;

        syncPlaylistItems(uploadsId, null, syncTime, cutoff);
    }

    private void upsertBroadcast(
            String videoId,
            String title,
            String thumbnailUrl,
            LocalDateTime publishedAt,
            String playlistTitle,
            LocalDateTime syncTime,
            Broadcast.Source source
    ) {
        broadcastRepository.findByVideoId(videoId).ifPresentOrElse(existing -> existing.syncFromYoutube(title, thumbnailUrl, publishedAt, playlistTitle, syncTime), () -> {
            Broadcast created = Broadcast.builder()
                    .videoId(videoId)
                    .title(title)
                    .url("https://www.youtube.com/watch?v=" + videoId)
                    .thumbnailUrl(thumbnailUrl)
                    .publishedAt(publishedAt)
                    .playlistTitle(playlistTitle) // null 가능
                    .source(source)
                    .lastSyncedAt(syncTime)
                    .build();
            broadcastRepository.save(created);
        });
    }

    private LocalDateTime parseYoutubeDateTime(String iso) {
        return OffsetDateTime.parse(iso).toLocalDateTime();
    }

    /**
     * 영상 목록 조회 (칩: 전체/명지대공식/명대뉴스(=방송국)). 전부 유튜브 콘텐츠.
     * - source 미지정/ALL : 방송국 + 공식 전체, 최신순
     * - source=OFFICIAL    : 명지대 공식(@mjuniv)만
     * - source=BROADCAST   : 명대 방송국만 (별칭 NEWS 도 동일)
     * 둘 다 broadcast 테이블에 source 컬럼으로 들어가 있어 단일 쿼리로 처리한다.
     */
    @Transactional(readOnly = true)
    public Page<BroadcastResponseDTO> getVideos(String source, Pageable pageable) {
        String src = (source == null || source.isBlank()) ? SOURCE_ALL : source.toUpperCase();

        Page<Broadcast> page;
        if (SOURCE_ALL.equals(src)) {
            page = broadcastRepository.findAll(pageable);
        } else if (SOURCE_OFFICIAL.equals(src)) {
            page = broadcastRepository.findBySource(Broadcast.Source.OFFICIAL, pageable);
        } else if (SOURCE_BROADCAST.equals(src) || SOURCE_NEWS.equals(src)) {
            page = broadcastRepository.findBySource(Broadcast.Source.BROADCAST, pageable);
        } else {
            throw new IllegalArgumentException("지원하지 않는 source 값입니다: " + source);
        }
        return page.map(this::toResponse);
    }

    private BroadcastResponseDTO toResponse(Broadcast b) {
        return BroadcastResponseDTO.builder()
                .source(b.getSource())
                .title(b.getTitle())
                .url(b.getUrl())
                .thumbnailUrl(b.getThumbnailUrl())
                .playlistTitle(b.getPlaylistTitle())
                .publishedAt(b.getPublishedAt())
                .build();
    }
}
