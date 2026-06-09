package nova.mjs.domain.thingo.banner.service.command;

import nova.mjs.domain.thingo.banner.dto.BannerDTO;

/**
 * 구글 시트 → DB 배너 동기화. 전체 교체(full replace) 방식.
 */
public interface BannerSyncService {

    /**
     * 시트 전체 행으로 배너 테이블을 교체한다.
     * @return 동기화된 배너 수
     */
    BannerDTO.SyncResult syncFromSheet(BannerDTO.SyncRequest request);
}
