package nova.mjs.domain.thingo.banner.controller;

import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.exception.BannerSyncException;
import nova.mjs.domain.thingo.banner.service.command.BannerSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BannerSyncControllerTest {

    private static final String VALID_TOKEN = "valid-secret-token";

    @Mock
    private BannerSyncService bannerSyncService;

    private BannerSyncController controller;

    private final BannerDTO.SyncRequest request =
            new BannerDTO.SyncRequest(List.of());

    @BeforeEach
    void setUp() {
        controller = new BannerSyncController(bannerSyncService);
        ReflectionTestUtils.setField(controller, "bannerSyncToken", VALID_TOKEN);
    }

    @Test
    @DisplayName("토큰이 일치하면 동기화 서비스를 호출한다")
    void should_동기화호출_when_토큰일치() {
        // given
        given(bannerSyncService.syncFromSheet(any()))
                .willReturn(BannerDTO.SyncResult.builder().syncedCount(0).build());

        // when
        controller.sync(VALID_TOKEN, request);

        // then
        verify(bannerSyncService).syncFromSheet(request);
    }

    @Test
    @DisplayName("토큰이 불일치하면 예외가 발생하고 동기화 서비스를 호출하지 않는다")
    void should_예외및_미호출_when_토큰불일치() {
        // when & then
        assertThatThrownBy(() -> controller.sync("wrong-token", request))
                .isInstanceOf(BannerSyncException.class);

        verify(bannerSyncService, never()).syncFromSheet(any());
    }

    @Test
    @DisplayName("토큰이 없으면 예외가 발생하고 동기화 서비스를 호출하지 않는다")
    void should_예외및_미호출_when_토큰누락() {
        // when & then
        assertThatThrownBy(() -> controller.sync(null, request))
                .isInstanceOf(BannerSyncException.class);

        verify(bannerSyncService, never()).syncFromSheet(any());
    }
}
