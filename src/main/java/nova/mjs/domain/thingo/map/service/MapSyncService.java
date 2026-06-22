package nova.mjs.domain.thingo.map.service;

import nova.mjs.domain.thingo.map.dto.MapSyncDTO;

/**
 * 구글 시트 → DB 명지도 동기화 서비스.
 */
public interface MapSyncService {

    /**
     * 시트 행 묶음을 받아 DB에 upsert 한다 (code/키 기준).
     *
     * @param request 탭별 행 묶음
     * @return 섹션별 처리 건수
     */
    MapSyncDTO.SyncResult syncFromSheet(MapSyncDTO.SyncRequest request);
}
