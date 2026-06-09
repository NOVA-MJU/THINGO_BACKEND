package nova.mjs.domain.thingo.banner.service;

import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.entity.Banner;
import nova.mjs.domain.thingo.banner.exception.BannerSyncException;
import nova.mjs.domain.thingo.banner.repository.BannerRepository;
import nova.mjs.domain.thingo.banner.service.command.BannerSyncServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BannerSyncServiceImplTest {

    @Mock
    private BannerRepository bannerRepository;

    @InjectMocks
    private BannerSyncServiceImpl bannerSyncService;

    private BannerDTO.SyncRow row(String title) {
        return BannerDTO.SyncRow.builder()
                .title(title)
                .oneLineIntro("소개")
                .imageUrl("https://thingo.kr/img.png")
                .category("이벤트")
                .displayOrder(1)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("정상 행들이 들어오면 기존 배너를 전체 삭제하고 새 배너를 일괄 저장한다")
    void should_전체교체_when_정상행들이_들어오면() {
        // given
        BannerDTO.SyncRequest request =
                new BannerDTO.SyncRequest(List.of(row("배너1"), row("배너2")));

        // when
        BannerDTO.SyncResult result = bannerSyncService.syncFromSheet(request);

        // then
        assertThat(result.getSyncedCount()).isEqualTo(2);
        verify(bannerRepository, times(1)).deleteAllInBatch();
        verify(bannerRepository, times(1)).saveAll(org.mockito.ArgumentMatchers.<List<Banner>>any());
    }

    @Test
    @DisplayName("제목이 비어 있는 행이 있으면 예외가 발생하고 삭제/저장이 수행되지 않는다(롤백)")
    void should_예외및_미수행_when_제목누락행이_있으면() {
        // given
        BannerDTO.SyncRequest request =
                new BannerDTO.SyncRequest(List.of(row("배너1"), row(" ")));

        // when & then
        assertThatThrownBy(() -> bannerSyncService.syncFromSheet(request))
                .isInstanceOf(BannerSyncException.class)
                .hasMessageContaining("2번째 행");

        verify(bannerRepository, never()).deleteAllInBatch();
        verify(bannerRepository, never()).saveAll(org.mockito.ArgumentMatchers.<List<Banner>>any());
    }

    @Test
    @DisplayName("날짜 형식이 잘못된 행이 있으면 예외가 발생하고 삭제/저장이 수행되지 않는다(롤백)")
    void should_예외및_미수행_when_날짜형식오류() {
        // given - startAt이 yyyy-MM-dd가 아님
        BannerDTO.SyncRow badDate = BannerDTO.SyncRow.builder()
                .title("배너1")
                .startAt("2026/01/01")
                .build();
        BannerDTO.SyncRequest request = new BannerDTO.SyncRequest(List.of(badDate));

        // when & then
        assertThatThrownBy(() -> bannerSyncService.syncFromSheet(request))
                .isInstanceOf(BannerSyncException.class)
                .hasMessageContaining("1번째 행");

        verify(bannerRepository, never()).deleteAllInBatch();
        verify(bannerRepository, never()).saveAll(org.mockito.ArgumentMatchers.<List<Banner>>any());
    }
}
