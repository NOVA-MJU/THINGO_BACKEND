package nova.mjs.domain.thingo.banner.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.entity.Banner;
import nova.mjs.domain.thingo.banner.repository.BannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class BannerQueryServiceImpl implements BannerQueryService {

    private final BannerRepository bannerRepository;

    /* ==========================================================
     * 활성 배너 조회
     *
     * 비즈니스 흐름:
     *  1) category 유무에 따라 전체/카테고리별 조회 (active=true, 순서 오름차순)
     *  2) DTO 변환
     * ========================================================== */
    @Override
    public List<BannerDTO.Response> getBanners(String category) {

        // 1) category가 비어 있으면 전체 활성 배너, 아니면 해당 카테고리만 (active=true, 순서 오름차순)
        List<Banner> banners = (category == null || category.isBlank())
                ? bannerRepository.findByActiveTrueOrderByDisplayOrderAsc()
                : bannerRepository.findByActiveTrueAndCategoryOrderByDisplayOrderAsc(category);

        // 2) 노출기간 필터 (오늘 기준 노출 대상만)
        LocalDate today = LocalDate.now();

        // 3) DTO 변환
        return banners.stream()
                .filter(banner -> banner.isVisibleOn(today))
                .map(BannerDTO.Response::from)
                .toList();
    }
}
