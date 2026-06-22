package nova.mjs.domain.thingo.map.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 건물/장소의 현재 운영 상태.
 *
 * DB에 저장하지 않고, 운영시간(OperatingHour) + 현재 시각을 비교해 요청 시점마다 계산한다.
 * 운영시간 데이터가 아예 없는 핀(예: 운영시간 미입력 외부 맛집)은 상태를 계산하지 않고 null로 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum OperatingStatus {

    PRE_OPEN("곧 운영 시작"),     // 운영 시작 30분 전
    OPEN("운영중"),
    PRE_CLOSE("곧 운영 종료"),    // 운영 종료 30분 전
    CLOSED("운영 종료"),          // 영업 시간 외 (다음 영업일 안내)
    ALWAYS_OPEN("24시간 운영"),
    HOLIDAY("휴무");

    private final String label;
}
