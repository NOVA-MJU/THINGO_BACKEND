package nova.mjs.domain.thingo.banner.service;

import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.entity.Banner;
import nova.mjs.domain.thingo.banner.repository.BannerRepository;
import nova.mjs.domain.thingo.banner.service.query.BannerQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BannerQueryServiceImplTest {

    @Mock
    private BannerRepository bannerRepository;

    @InjectMocks
    private BannerQueryServiceImpl bannerQueryService;

    private Banner banner() {
        return Banner.from(BannerDTO.SyncRow.builder()
                .title("배너")
                .category("이벤트")
                .displayOrder(1)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("category가 null이면 전체 활성 배너를 순서대로 조회한다")
    void should_전체조회_when_category가_null() {
        // given
        given(bannerRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .willReturn(List.of(banner()));

        // when
        List<BannerDTO.Response> result = bannerQueryService.getBanners(null);

        // then
        assertThat(result).hasSize(1);
        verify(bannerRepository).findByActiveTrueOrderByDisplayOrderAsc();
        verify(bannerRepository, never())
                .findByActiveTrueAndCategoryOrderByDisplayOrderAsc(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("노출기간이 지난(active=true) 배너는 결과에서 제외된다")
    void should_제외_when_노출기간만료() {
        // given - active지만 종료일이 과거인 배너
        Banner expired = Banner.from(BannerDTO.SyncRow.builder()
                .title("만료배너")
                .active(true)
                .endAt("2000-01-01")
                .build());
        given(bannerRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .willReturn(List.of(expired));

        // when
        List<BannerDTO.Response> result = bannerQueryService.getBanners(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("category가 주어지면 해당 카테고리의 활성 배너만 조회한다")
    void should_카테고리조회_when_category가_주어지면() {
        // given
        given(bannerRepository.findByActiveTrueAndCategoryOrderByDisplayOrderAsc("이벤트"))
                .willReturn(List.of(banner()));

        // when
        List<BannerDTO.Response> result = bannerQueryService.getBanners("이벤트");

        // then
        assertThat(result).hasSize(1);
        verify(bannerRepository).findByActiveTrueAndCategoryOrderByDisplayOrderAsc("이벤트");
        verify(bannerRepository, never()).findByActiveTrueOrderByDisplayOrderAsc();
    }
}
