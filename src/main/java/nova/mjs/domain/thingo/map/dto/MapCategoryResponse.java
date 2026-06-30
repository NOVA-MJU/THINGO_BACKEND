package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;

import java.util.List;

/**
 * 카테고리(칩) 응답 모음.
 *
 * - Chip : 칩 1개. 상단 퀵메뉴와 전체 카테고리 바텀시트가 공용으로 쓴다.
 * - Group: 전체 카테고리 바텀시트의 한 섹션(식사/학습·휴식/편의)과 그 안의 칩들.
 */
public class MapCategoryResponse {

    /** 칩 1개 (상단 퀵메뉴 / 전체 카테고리 공용) */
    @Getter
    @Builder
    public static class Chip {
        /** 칩 코드 (프론트가 칩 클릭 시 이 코드로 목록을 요청) */
        private final String code;
        /** 칩 표시 이름 */
        private final String label;
        /** 아이콘 키 */
        private final String iconKey;
        /** 클릭 결과 종류 (PLACE_LIST / BUILDING_LIST / BUS) */
        private final String resultType;
        /** 부제/출처 (예: "by. 명월"). 없으면 null */
        private final String subtitle;
        /** 메인 홈 툴팁 문구. 없으면 null */
        private final String tooltipText;

        public static Chip from(Category category) {
            return Chip.builder()
                    .code(category.getCode())
                    .label(category.getLabel())
                    .iconKey(category.getIconKey())
                    .resultType(category.getResultType().name())
                    .subtitle(category.getSubtitle())
                    .tooltipText(category.getTooltipText())
                    .build();
        }
    }

    /** 전체 카테고리 바텀시트의 한 그룹 (헤더 + 칩 목록) */
    @Getter
    @Builder
    public static class Group {
        /** 그룹 코드 */
        private final String code;
        /** 그룹 이름 (예: "식사 (F&B)") */
        private final String name;
        /** 그룹에 속한 칩들 */
        private final List<Chip> chips;

        public static Group of(CategoryGroup group, List<Chip> chips) {
            return Group.builder()
                    .code(group.getCode())
                    .name(group.getName())
                    .chips(chips)
                    .build();
        }
    }
}
