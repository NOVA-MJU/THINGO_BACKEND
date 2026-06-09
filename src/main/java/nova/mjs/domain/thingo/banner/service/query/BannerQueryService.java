package nova.mjs.domain.thingo.banner.service.query;

import nova.mjs.domain.thingo.banner.dto.BannerDTO;

import java.util.List;

/**
 * 배너 공개 조회 (앱).
 */
public interface BannerQueryService {

    /**
     * 활성 배너 목록. category가 null이면 전체, 아니면 해당 카테고리만. 노출 순서 오름차순.
     */
    List<BannerDTO.Response> getBanners(String category);
}
