package nova.mjs.domain.thingo.dday.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.calendar.dto.MjuCalendarDTO;
import nova.mjs.domain.thingo.calendar.service.MjuCalendarService;
import nova.mjs.domain.thingo.dday.dto.DDayDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DDayService {

    // 기본 노출 개수 (프론트가 limit 미지정 시)
    private static final int DEFAULT_LIMIT = 4;

    // 다른 도메인(calendar)은 Service 메서드를 통해서만 접근
    private final MjuCalendarService calendarService;

    // 기본 개수(4)로 조회 - 하위 호환용
    public List<DDayDto.Response> getDDays() {
        return getDDays(DEFAULT_LIMIT);
    }

    // 임박순 상위 limit개의 디데이 목록 조회
    // - limit이 0 이하면 기본값(4) 적용
    public List<DDayDto.Response> getDDays(int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        LocalDate today = LocalDate.now();

        // 1. 진행 중 + 예정 학사일정 수집 (종료일 >= today)
        List<MjuCalendarDTO> calendars = calendarService.getOngoingAndUpcoming(today);

        // 2. 디데이 응답으로 변환
        // 3. 카운트다운 대상일(임박순) 정렬 - 동순위는 수집 순서(시작일/종료일/id) 유지(안정 정렬)
        // 4. 상위 limit개만 반환
        return calendars.stream()
                .map(calendar -> DDayDto.Response.fromCalendar(calendar, today))
                .sorted(Comparator.comparing(DDayDto.Response::getTargetDate))
                .limit(effectiveLimit)
                .toList();
    }
}
