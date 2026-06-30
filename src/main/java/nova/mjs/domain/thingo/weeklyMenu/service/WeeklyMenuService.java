package nova.mjs.domain.thingo.weeklyMenu.service;

import lombok.extern.log4j.Log4j2;
import nova.mjs.util.exception.ErrorCode;
import nova.mjs.domain.thingo.weeklyMenu.DTO.WeeklyMenuResponseDTO;
import nova.mjs.domain.thingo.weeklyMenu.entity.WeeklyMenu;
import nova.mjs.domain.thingo.weeklyMenu.entity.enumList.MenuCategory;
import nova.mjs.domain.thingo.weeklyMenu.event.WeeklyMenuCrawledEvent;
import nova.mjs.domain.thingo.weeklyMenu.exception.WeeklyMenuNotFoundException;
import nova.mjs.domain.thingo.weeklyMenu.repository.WeeklyMenuRepository;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
public class WeeklyMenuService {
    //1. 요청값이 뭔지(파라미터, request body) - 없음
    //2. 요청값으로 뭘 할건지 -> 없는데 뭘 합니까
    //3. 응답값이 뭔지 -> 날짜, 카테고리, 메뉴(리스트)
    //4. db에 저장할지 판단 -> 필요하면 엔티티에 있는 메서드로 객체 생성 : 날짜, 카테고리, 메뉴(리스트)
    //5. 레퍼에 접근해서 엔티티 값을 넣어줘

    private final WeeklyMenuRepository menuRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WeeklyMenuService(WeeklyMenuRepository menuRepository, ApplicationEventPublisher eventPublisher) {
        this.menuRepository = menuRepository;
        this.eventPublisher = eventPublisher;
    }

    // URL 상수 선언
    private static final String url = "https://www.mju.ac.kr/mjukr/8595/subview.do";

    @Transactional
    public List<WeeklyMenuResponseDTO> crawlWeeklyMenu() {
        List<WeeklyMenu> weeklyMenus = new ArrayList<>();

        try {
            Document doc;

            LocalDate today = LocalDate.now();
            DayOfWeek dayOfWeek = today.getDayOfWeek();

            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                LocalDate currentWeekMonday = today.minusDays(dayOfWeek == DayOfWeek.SATURDAY ? 5 : 6);
                String mondayParam = currentWeekMonday.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));

                log.info("주말이므로 다음 주 식단 페이지를 직접 요청합니다. monday={}, week=next", mondayParam);

                doc = Jsoup.connect("https://www.mju.ac.kr/diet/mjukr/10/view.do")
                        .data("monday", mondayParam)
                        .data("week", "next")
                        .post();
            } else {
                doc = Jsoup.connect(url).get();
            }
            Element tableWrap = doc.selectFirst(".tableWrap.marT50");

            if (tableWrap == null) {
                log.error("테이블을 포함하는 div를 찾을 수 없습니다.");
            }

            Element table = tableWrap.selectFirst("table");
            if (table == null) {
                log.error("테이블을 찾을 수 없습니다.");
            }

            Elements rows = table.select("tbody tr");
            if (rows.isEmpty()){
                log.error("식단 데이터를 찾을 수 없습니다.");
                throw new WeeklyMenuNotFoundException("식단 데이터를 찾을 수 없습니다.", ErrorCode.WEEKLYMENU_NOT_FOUND);
            }
            String currentDate = null;

            for (Element row : rows) {
                Element dateCell = row.selectFirst("th[rowspan]"); //날짜
                if (dateCell != null) {
                    currentDate = dateCell.text().trim(); //날짜 최신화
                }

                Elements cells = row.select("td"); //카테고리가 있는 class
                if (!cells.isEmpty()) {
                    String category = cells.get(0).text().trim(); //카테고리 수집
                    MenuCategory menuCategory = mapCategory(category); // 카테고리 변환

                    if (menuCategory == null) {
                        log.warn("정의되지 않은 카테고리: {}", category);
                        continue; // 변환되지 않은 카테고리는 무시
                    }

                    Element menuCell = row.selectFirst("td.alignL"); //메뉴가 있는 class
                    List<String> menuContent = menuCell != null
                            ? Arrays.stream(menuCell.html().split("<br>")) // 줄바꿈 기준으로 분리
                            .map(String::trim) // 양쪽 공백 제거
                            .map(content -> content.replace("&amp;", "&")) // &amp;를 &로 변환
                            .toList() // 리스트로 변환
                            : Collections.singletonList("등록된 식단 내용이 없습니다."); // 메뉴 수집

                    if (menuContent.isEmpty()){
                        log.error("메뉴 데이터가 비어 있습니다.");
                    }
                    WeeklyMenu weeklyMenu = WeeklyMenu.create(currentDate, menuCategory, menuContent);
                    weeklyMenus.add(weeklyMenu);
                }
            }

            //save() : 영속성 컨텍스트의 cache에 먼저 저장 -> 나중에 flush()
            //vs. saveAndFlush() : 즉시 db에 반영
            if (!weeklyMenus.isEmpty()){
                deleteAllWeeklyMenus();
                log.info("기존 식단 데이터를 삭제했습니다.");

                menuRepository.saveAll(weeklyMenus);
                log.info("새로운 식단 데이터를 저장했습니다. 총 {} 개의 메뉴", weeklyMenus.size());

                // 학식 알림: 크롤 성공 이벤트 발행(트랜잭션 커밋 후 알림 도메인이 수신).
                // 같은 주 반복 크롤링 시 중복 알림은 수신측이 contentSignature 로 판별한다.
                eventPublisher.publishEvent(
                        new WeeklyMenuCrawledEvent(weeklyMenus.size(), buildContentSignature(weeklyMenus)));
            } else{
                log.warn("크롤링된 데이터가 없어 기존 데이터를 삭제하지 않았습니다.");
            }

        } catch (Exception e) {
            log.error("크롤링 오류 = {}", e.getMessage(), e);
        }
        return WeeklyMenuResponseDTO.fromEntityToList(weeklyMenus);
    }

    /**
     * 크롤된 식단 전체의 내용 지문(날짜+끼니+메뉴 기반).
     * 같은 주를 반복 크롤링하면 동일 값이 나와 수신측이 중복 알림을 건너뛸 수 있다.
     */
    private String buildContentSignature(List<WeeklyMenu> weeklyMenus) {
        String joined = weeklyMenus.stream()
                .map(menu -> menu.getDate() + "|" + menu.getMenuCategory() + "|" + String.join(",", menu.getMeals()))
                .sorted()
                .collect(Collectors.joining(";"));
        return Integer.toHexString(joined.hashCode());
    }

    private MenuCategory mapCategory(String category) {
        switch (category) {
            case "조식":
                return MenuCategory.BREAKFAST; // Enum에 정의된 값으로 매핑
            case "중식":
                return MenuCategory.LUNCH; // Enum에 정의된 값으로 매핑
            case "석식":
                return MenuCategory.DINNER; // Enum에 정의된 값으로 매핑
            default:
                return null; // 매핑되지 않은 값은 null 반환
        }
    }

    //식단을 크롤링했을 때 중복 발생을 고려한 식단 데이터 삭제하는 메서드
    @Transactional
    public void deleteAllWeeklyMenus(){
        menuRepository.deleteAll();
    }

    //DB에서 해당 주 전체 식단 데이터를 가져오는 메서드 (크롤링 시점 기준 1주일치)
    public List<WeeklyMenuResponseDTO> getAllWeeklyMenus() {
        // 크롤링 순서(월~금, 조/중/석)를 보존하기 위해 id 오름차순 정렬
        List<WeeklyMenu> menus = menuRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        if (menus.isEmpty()) {
            throw new WeeklyMenuNotFoundException("저장된 식단 정보가 없습니다.", ErrorCode.WEEKLYMENU_NOT_FOUND);
        }

        return WeeklyMenuResponseDTO.fromEntityToList(menus);
    }
}

