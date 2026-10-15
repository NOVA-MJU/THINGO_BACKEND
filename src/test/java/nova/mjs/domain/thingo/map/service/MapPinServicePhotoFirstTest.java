package nova.mjs.domain.thingo.map.service;

import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 테마지도 목록에서 사진 있는 장소를 먼저 보여주는 정렬 규칙 */
class MapPinServicePhotoFirstTest {

    private final Category cafe = Category.ofChip("daedong-cafe", CategoryGroup.of("food", "식사", 1),
            "카페", null, null, null, CategoryResultType.PLACE_LIST, false, 1);

    private Pin place(String code, String imageUrl) {
        return Pin.ofExternalPlace(code, cafe, code, 37.58, 126.92, imageUrl, null, null);
    }

    @Test
    @DisplayName("사진 있는 장소가 먼저 오고, 각 그룹 안에서는 원래 순서를 지킨다")
    void photoFirst_keepsOrderWithinGroups() {
        List<Pin> pins = List.of(
                place("a", null),
                place("b", "https://thingo.kr/b.jpg"),
                place("c", " "),
                place("d", "https://thingo.kr/d.jpg"),
                place("e", null));

        assertThat(MapPinService.photoFirst(pins)).extracting(Pin::getCode)
                .containsExactly("b", "d", "a", "c", "e");
    }
}
