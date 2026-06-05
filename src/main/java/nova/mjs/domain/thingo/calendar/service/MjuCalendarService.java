package nova.mjs.domain.thingo.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.calendar.dto.MjuCalendarDTO;
import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import nova.mjs.domain.thingo.calendar.exception.CalendarInvalidInputException;
import nova.mjs.domain.thingo.calendar.repository.MjuCalendarRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MjuCalendarService {

    private static final String BASE =
            "https://www.mju.ac.kr/schdulmanage/mjukr/4/yearSchdul.do";
    private static final Pattern DATE_RGX =
            Pattern.compile("\\.(\\d{2}) \\.(\\d{2})");

    private final RestTemplate restTemplate;
    private final MjuCalendarRepository calendarRepository;

    // 디데이 기능용: 기준일(today) 이후까지 진행되는(종료일 >= today) 일정만 임박순으로 반환
    public List<MjuCalendarDTO> getOngoingAndUpcoming(LocalDate today) {
        return calendarRepository
                .findByEndDateGreaterThanEqualOrderByStartDateAscEndDateAscIdAsc(today)
                .stream()
                .map(MjuCalendarDTO::fromEntity)
                .toList();
    }

    public Page<MjuCalendarDTO> getCalendarsFiltered(Integer year, Pageable pageable) {
        Page<MjuCalendar> result = (year != null)
                ? calendarRepository.findByYear(year, pageable)
                : calendarRepository.findAll(pageable);
        return result.map(MjuCalendarDTO::fromEntity);
    }

    @Transactional
    public void refresh(int fromYear, int toYear) {
        calendarRepository.deleteAll();  // 전체 초기화 후 재갱신
        for (int currentYear = fromYear; currentYear <= toYear; currentYear++) {
            List<MjuCalendarDTO> list = crawlYear(currentYear);
            list.forEach(dto -> calendarRepository.save(MjuCalendar.create(dto)));
            log.info("{}학년도 일정 {}건 저장 완료", currentYear, list.size());
        }
    }

    private List<MjuCalendarDTO> crawlYear(int year) {
        String html = restTemplate.getForObject(BASE + "?year=" + year, String.class);
        Document doc = Jsoup.parse(html);

        List<MjuCalendarDTO> result = new ArrayList<>();

        for (Element li : doc.select("#timeTableList li")) {
            Element strong = li.selectFirst("dl dt strong");
            if (strong == null) continue;

            for (Element item : li.select("dd .text-list li")) {
                Element dateElement = item.selectFirst("strong");
                if (dateElement == null) continue;

                String dateRange = dateElement.text().trim();
                LocalDate[] dates = parseDateRange(year, dateRange);
                if (dates == null) continue;

                String raw = item.text().replace(dateRange, "").trim();
                String normalized = normalizeDescription(raw);

                result.add(new MjuCalendarDTO(year, dates[0], dates[1], normalized));
            }
        }
        return result;
    }

    /** “.MM .dd ~ .MM .dd” 또는 “.MM .dd” 를 LocalDate 로 변환 */
    private LocalDate[] parseDateRange(int academicYear, String source) {
        Matcher matcher = DATE_RGX.matcher(source);
        List<int[]> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(new int[] {
                    Integer.parseInt(matcher.group(1)),   // month
                    Integer.parseInt(matcher.group(2))    // day
            });
        }
        if (parts.isEmpty()) return null;

        LocalDate start = LocalDate.of(academicYear, parts.get(0)[0], parts.get(0)[1]);
        LocalDate end;

        if (parts.size() == 1) {
            end = start;
        } else {
            int endMonth = parts.get(1)[0];
            int endDay = parts.get(1)[1];
            int endYear = (endMonth < start.getMonthValue()) ? academicYear + 1 : academicYear;
            end = LocalDate.of(endYear, endMonth, endDay);
        }

        return new LocalDate[] { start, end };
    }

    // 월별 교차 조회(11.30~12.3 등 연결) + 상호배타 카테고리 분류
    public MjuCalendarDTO.MonthlyResponse getMonthlyByYearAndMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new CalendarInvalidInputException();
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate monthStartDate = yearMonth.atDay(1);
        LocalDate monthEndDate = yearMonth.atEndOfMonth();

        List<MjuCalendar> events = calendarRepository.findMonthlySchedule(monthStartDate, monthEndDate);

        // 변수명 명확화
        List<MjuCalendarDTO.Category> generalCommon = new ArrayList<>();     // 전체(공통)
        List<MjuCalendarDTO.Category> undergraduateOnly = new ArrayList<>(); // 학부 전용
        List<MjuCalendarDTO.Category> graduateOnly = new ArrayList<>();      // 대학원 전용
        List<MjuCalendarDTO.Category> holidayOnly = new ArrayList<>();       // 휴일 전용

        for (MjuCalendar event : events) {
            ParsedResult parsed = parseAndClassify(event.getDescription());

            MjuCalendarDTO.Category item = MjuCalendarDTO.Category.builder()
                    .id(event.getId())
                    .startDate(event.getStartDate())
                    .endDate(event.getEndDate())
                    .description(parsed.displayDescription()) // ← 브래킷 유지된 설명
                    // .rawTags(parsed.tags())                // DTO에 필드가 있으면 주석 해제
                    // .matchedRules(parsed.matchedRuleNames())
                    .build();

            // 휴일 분류 판단은 parsed.classifyText() / flags 로 이미 끝남
            if (!parsed.matchedRuleNames().isEmpty() && !parsed.isAcademicActivity()) {
                holidayOnly.add(item);
                continue;
            }
            boolean hasUndergrad = parsed.tags().contains("학부");
            boolean hasGraduate  = parsed.tags().contains("대학원");

            if ((hasUndergrad && hasGraduate) || (!hasUndergrad && !hasGraduate)) {
                generalCommon.add(item);
            } else if (hasUndergrad) {
                undergraduateOnly.add(item);
            } else if (hasGraduate) {
                graduateOnly.add(item);
            }
        }

        // 정렬 일관성 보장(시작일, 종료일, id)
        Comparator<MjuCalendarDTO.Category> sorter =
                Comparator.comparing(MjuCalendarDTO.Category::getStartDate)
                        .thenComparing(MjuCalendarDTO.Category::getEndDate)
                        .thenComparing(MjuCalendarDTO.Category::getId);

        generalCommon.sort(sorter);
        undergraduateOnly.sort(sorter);
        graduateOnly.sort(sorter);
        holidayOnly.sort(sorter);

        return MjuCalendarDTO.MonthlyResponse.builder()
                .year(year)
                .month(month)
                .all(generalCommon)       // DTO 필드는 유지(all/undergrad/graduate/holiday)
                .undergrad(undergraduateOnly)
                .graduate(graduateOnly)
                .holiday(holidayOnly)
                .build();
    }

    // =========================
    // 파싱/분류 유틸
    // =========================

    // 선두 [학부], [대학원], [학부·대학원] 등 추출
    private static final Pattern LEADING_BRACKETS_PATTERN =
            Pattern.compile("^\\s*((\\[[^\\]]+])+\\s*)+");

    // 브래킷 내부 태그 구분자(·, ㆍ, /, ,)
    private static final Pattern BRACKET_TAG_SPLITTER =
            Pattern.compile("\\s*[·ㆍ/,]\\s*");

    // 휴일 분류 규칙(내용 기반, “쉬는 것”은 모두 휴일로)
    // - '절'/'날' 같은 광범위 키워드는 제거 (오탐 방지: '계절수업기간')
    private static final Map<String, Pattern> HOLIDAY_RULES = Map.ofEntries(
            Map.entry("contains_vacation",
                    Pattern.compile(".*(방학|집중\\s*휴무).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
            Map.entry("contains_public_holiday",
                    Pattern.compile(".*(공휴일|연휴|대체\\s*공휴일|임시\\s*공휴일).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
            Map.entry("contains_named_holiday",
                    Pattern.compile(".*(신정|설날|추석|3\\.1절|현충일|광복절|개천절|한글날|성탄절).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)),
            Map.entry("contains_election",
                    Pattern.compile(".*선거.*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
    );

    // 학사활동(수업/시험/강의평가/수강/논문/모집 등)은 휴일이 아님
    private static final Pattern ACADEMIC_ACTIVITY =
            Pattern.compile(".*(수업|강의|강의평가|중간고사|기말고사|보강|수강|수강신청|수강정정|재수강|등록|휴학|복학|논문|심사|제출|모집|오리엔테이션|졸업|학위|개강|개시|수업일수|계절수업).*",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private record ParsedResult(
            Set<String> tags,
            String displayDescription,     // ← 사용자에게 보여줄 설명(브래킷 유지)
            String classifyText,           // ← 분류 판단에만 쓰는 텍스트(브래킷 제거)
            Set<String> matchedRuleNames,
            boolean isAcademicActivity
    ) {}

    // 설명에서 선두 브래킷 태그 파싱 + 응답용 설명 정리 + 휴일 규칙 매칭
    private ParsedResult parseAndClassify(String rawDescription) {
        String normalizedDescription = normalizeDescription(rawDescription);

        // 표시용 설명은 그대로(브래킷 유지)
        String displayDescription = normalizedDescription;

        // 분류 판단에 사용할 텍스트는 선두 브래킷만 제거
        String classifyText = normalizedDescription;

        // 1) 선두 브래킷 태그 파싱
        Set<String> tags = new LinkedHashSet<>();
        Matcher leadingMatcher = LEADING_BRACKETS_PATTERN.matcher(normalizedDescription);
        if (leadingMatcher.find()) {
            String leadingBlock = leadingMatcher.group(0);
            Matcher each = Pattern.compile("\\[([^\\]]+)]").matcher(leadingBlock);
            while (each.find()) {
                String inside = each.group(1);
                for (String token : BRACKET_TAG_SPLITTER.split(inside)) {
                    String tag = normalizeTag(token);
                    if (!tag.isBlank()) {
                        if (tag.contains("학부")) tags.add("학부");
                        if (tag.contains("대학원")) tags.add("대학원");
                    }
                }
            }
            // 분류 판단용 텍스트에서만 선두 브래킷 제거
            classifyText = normalizedDescription.substring(leadingMatcher.end()).trim();
        }

        // 2) 학사활동 여부(휴일보다 우선)
        boolean isAcademicActivity = ACADEMIC_ACTIVITY.matcher(classifyText).matches();

        // 3) 휴일 규칙 매칭(쉬는 것 전부 휴일) — 단, 학사활동이면 휴일로 보지 않음
        Set<String> matchedRuleNames = new LinkedHashSet<>();
        if (!isAcademicActivity) {
            for (Map.Entry<String, Pattern> entry : HOLIDAY_RULES.entrySet()) {
                if (entry.getValue().matcher(classifyText).matches()) {
                    matchedRuleNames.add(entry.getKey());
                }
            }
        }

        return new ParsedResult(tags, displayDescription, classifyText, matchedRuleNames, isAcademicActivity);
    }

    // <br> 정리, 유니코드 정규화, 다중 공백 제거
    private String normalizeDescription(String raw) {
        if (raw == null) return "";
        String replaced = raw
                .replace("<br>", " ")
                .replace("<br/>", " ")
                .replace("<br />", " ");
        return Normalizer.normalize(replaced, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
    }

    // 태그 문자열 정규화
    private String normalizeTag(String token) {
        if (token == null) return "";
        String noSpaces = token.replaceAll("\\s+", "");
        if (noSpaces.contains("학부")) return "학부";
        if (noSpaces.contains("대학원")) return "대학원";
        return noSpaces;
    }
}
