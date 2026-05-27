package nova.mjs.domain.thingo.notice.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.notice.dto.NoticeResponseDto;
import nova.mjs.domain.thingo.notice.entity.Notice;
import nova.mjs.domain.thingo.notice.exception.NoticeNotFoundException;
import nova.mjs.domain.thingo.notice.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public Page<NoticeResponseDto.Summary> getNotices(String category, Integer year, int page, int size, String sort) {
        if (category == null || category.isEmpty()) {
            throw new NoticeNotFoundException();
        }

        // category가 null 또는 빈 문자열이면 "all"로 처리
        if (category == null || category.trim().isEmpty()) {
            category = "all";
        }

        boolean isAll = "all".equalsIgnoreCase(category);

        Sort.Direction direction = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(
                Math.max(0, page), // 음수 방어
                Math.max(1, size), // 최소 1개 보장
                Sort.by(direction, "date")
        );


        Page<Notice> notices;

        if (year != null) {
            // 연도 필터 있을 경우
            LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0);
            LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);

            notices = isAll
                    ? noticeRepository.findByDateBetween(startDate, endDate, pageable) // 전체 조회
                    : noticeRepository.findByCategoryAndDateBetween(category, startDate, endDate, pageable); // 카테고리별 조회
        } else {
            // 연도 필터 없을 경우
            notices = isAll
                    ? noticeRepository.findAll(pageable) // 전체 조회
                    : noticeRepository.findByCategory(category, pageable); // 카테고리별 조회
        }


        if (notices.isEmpty()) {
            throw new NoticeNotFoundException();
        }

        return notices.map(NoticeResponseDto.Summary::fromEntity);

    }

    /**
     * HOT 공지 조회
     *
     * 정책
     * - 최근 1개월(date >= now - 1m) 이내 공지만 대상
     * - viewCount DESC, 동률은 date DESC (리포지토리 쿼리 고정)
     * - 페이지/사이즈는 프론트가 지정 (기본 size=6은 Controller 기본값)
     */
    public List<NoticeResponseDto.Summary> getHotNotices(Pageable pageable) {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);

        return noticeRepository.findHotNoticesWithinMonth(oneMonthAgo, pageable).stream()
                .map(NoticeResponseDto.Summary::fromEntity)
                .toList();
    }
}
