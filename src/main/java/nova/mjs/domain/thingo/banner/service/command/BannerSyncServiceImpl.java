package nova.mjs.domain.thingo.banner.service.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import nova.mjs.domain.thingo.banner.dto.BannerDTO;
import nova.mjs.domain.thingo.banner.entity.Banner;
import nova.mjs.domain.thingo.banner.exception.BannerSyncException;
import nova.mjs.domain.thingo.banner.repository.BannerRepository;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
@Transactional(readOnly = true)
public class BannerSyncServiceImpl implements BannerSyncService {

    private final BannerRepository bannerRepository;

    /* ==========================================================
     * 시트 → DB 전체 교체
     *
     * 비즈니스 흐름:
     *  1) 행별 검증 (제목 필수) - 실패 시 전체 롤백
     *  2) 기존 배너 전체 삭제
     *  3) 새 배너 일괄 저장
     *  4) 동기화 건수 반환
     * ========================================================== */
    @Override
    @Transactional
    public BannerDTO.SyncResult syncFromSheet(BannerDTO.SyncRequest request) {

        List<BannerDTO.SyncRow> rows = request.getRows();

        // 1) 행별 검증 + 엔티티 변환 (실패 시 어떤 행이 왜 실패했는지 명시하여 전체 롤백)
        List<Banner> banners = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            BannerDTO.SyncRow row = rows.get(index);

            // 1-1) 제목 필수
            if (row.getTitle() == null || row.getTitle().isBlank()) {
                throw new BannerSyncException(
                        (index + 1) + "번째 행: 제목이 비어 있습니다.",
                        ErrorCode.BANNER_SYNC_INVALID_ROW
                );
            }

            // 1-2) 날짜 형식 검증 (yyyy-MM-dd)
            try {
                banners.add(Banner.from(row));
            } catch (DateTimeParseException e) {
                throw new BannerSyncException(
                        (index + 1) + "번째 행: 노출기간 날짜 형식이 올바르지 않습니다. (yyyy-MM-dd)",
                        ErrorCode.BANNER_SYNC_INVALID_ROW
                );
            }
        }

        // 2) 기존 배너 전체 삭제 (전체 교체 방식 - 시트가 곧 진실)
        bannerRepository.deleteAllInBatch();

        // 3) 새 배너 일괄 저장
        bannerRepository.saveAll(banners);

        log.info("[배너 동기화 완료] 동기화 건수={}", banners.size());

        return BannerDTO.SyncResult.builder()
                .syncedCount(banners.size())
                .build();
    }
}
