package nova.mjs.domain.thingo.weeklyMenu.event;

/**
 * 학식 크롤링이 성공해 새 식단이 저장됐을 때 발행되는 도메인 이벤트.
 *
 * weeklyMenu 도메인은 이 이벤트만 발행하고 알림 도메인을 직접 알지 못한다(디커플).
 * contentSignature 는 크롤된 식단 전체의 지문으로, 같은 주를 반복 크롤링할 때
 * 변경 여부를 구독자 알림 단계에서 판별하는 데 쓴다(동일하면 알림 생략).
 *
 * @param menuCount        저장된 식단 row 수
 * @param contentSignature 식단 내용 지문(날짜+끼니+메뉴 기반)
 */
public record WeeklyMenuCrawledEvent(int menuCount, String contentSignature) {
}
