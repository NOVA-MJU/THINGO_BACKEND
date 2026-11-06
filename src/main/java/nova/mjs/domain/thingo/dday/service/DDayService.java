package nova.mjs.domain.thingo.dday.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.calendar.dto.MjuCalendarDTO;
import nova.mjs.domain.thingo.calendar.service.MjuCalendarService;
import nova.mjs.domain.thingo.dday.dto.DDayDto;
import nova.mjs.domain.thingo.member.entity.Member;
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
        return getDDays(DEFAULT_LIMIT, null);
    }

    // 임박순 상위 limit개의 디데이 목록 조회
    // - limit이 0 이하면 기본값(4) 적용
    // - studentType이 있으면 그 대상(학생/대학원생) 일정과 공통 일정만 남긴다. 없으면 전부 (구버전 앱 호환)
    public List<DDayDto.Response> getDDays(int limit, Member.StudentType studentType) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        LocalDate today = LocalDate.now();

        // 1. 진행 중 + 예정 학사일정 수집 (종료일 >= today)
        List<MjuCalendarDTO> calendars = calendarService.getOngoingAndUpcoming(today);

        // 2. 디데이 응답으로 변환
        // 3. 카운트다운 대상일(임박순) 정렬 - 동순위는 수집 순서(시작일/종료일/id) 유지(안정 정렬)
        // 4. 상위 limit개만 반환
        boolean graduate = studentType == Member.StudentType.GRADUATE_STUDENT;
        return calendars.stream()
                .filter(calendar -> studentType == null
                        || calendarService.isForAudience(calendar.getDescription(), graduate))
                .map(calendar -> DDayDto.Response.fromCalendar(calendar, today))
                .sorted(Comparator.comparing(DDayDto.Response::getTargetDate))
                .limit(effectiveLimit)
                .toList();
    }
}
