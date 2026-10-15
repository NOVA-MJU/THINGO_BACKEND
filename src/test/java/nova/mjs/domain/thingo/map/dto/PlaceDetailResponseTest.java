package nova.mjs.domain.thingo.map.dto;

import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 장소 상세 갤러리(imageUrls) 구성 규칙. DB 없이 도는 단위 테스트라 Docker 없는 환경에서도 검증된다 */
class PlaceDetailResponseTest {

    private final Category smoking = Category.ofChip("smoking", CategoryGroup.of("convenience", "편의", 1),
            "흡연 부스", null, null, "SmokingIcon", CategoryResultType.PLACE_LIST, false, 1);

    private Pin place(String imageUrl) {
        return Pin.ofExternalPlace("p-smoking-ext-01", smoking, "흡연부스", 37.58, 126.92, imageUrl, null, null);
    }

    @Test
    @DisplayName("갤러리를 등록한 장소는 등록 순서대로 내려준다")
    void gallery_inOrder() {
        Pin pin = place("https://thingo.kr/cover.jpg");
        pin.replaceImageUrls(List.of("https://thingo.kr/a.jpg", "https://thingo.kr/b.jpg"));

        assertThat(PlaceDetailResponse.of(pin, false, null, null).getImageUrls())
                .containsExactly("https://thingo.kr/a.jpg", "https://thingo.kr/b.jpg");
    }

    @Test
    @DisplayName("갤러리가 없으면 대표 사진 한 장, 사진이 아예 없으면 빈 배열")
    void gallery_fallsBackToCover() {
        assertThat(PlaceDetailResponse.of(place("https://thingo.kr/cover.jpg"), false, null, null).getImageUrls())
                .containsExactly("https://thingo.kr/cover.jpg");
        assertThat(PlaceDetailResponse.of(place(null), false, null, null).getImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("갤러리를 다시 교체하면 이전 사진은 남지 않는다")
    void replace_overwrites() {
        Pin pin = place(null);
        pin.replaceImageUrls(List.of("https://thingo.kr/a.jpg", "https://thingo.kr/b.jpg"));
        pin.replaceImageUrls(List.of("https://thingo.kr/c.jpg"));

        assertThat(PlaceDetailResponse.of(pin, false, null, null).getImageUrls()).containsExactly("https://thingo.kr/c.jpg");
    }

    @Test
    @DisplayName("네이버 장소 핀(dd-{id})만 네이버 지도 링크를 내려준다")
    void naverPlaceUrl_onlyForNaverPlaces() {
        assertThat(PlaceDetailResponse.naverPlaceUrlOf("dd-1029271253"))
                .isEqualTo("https://map.naver.com/p/entry/place/1029271253");
        assertThat(PlaceDetailResponse.naverPlaceUrlOf("p-smoking-ext-01")).isNull();
        assertThat(PlaceDetailResponse.naverPlaceUrlOf("dd-abc")).isNull();
        assertThat(PlaceDetailResponse.of(place(null), false, null, null).getNaverPlaceUrl()).isNull();
    }
}
