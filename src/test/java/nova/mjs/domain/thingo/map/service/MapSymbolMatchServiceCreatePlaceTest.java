package nova.mjs.domain.thingo.map.service;

import nova.mjs.domain.thingo.map.dto.SymbolMatchResponse;
import nova.mjs.domain.thingo.map.dto.SymbolPlaceCreateRequest;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.exception.SymbolPlaceInvalidException;
import nova.mjs.domain.thingo.map.repository.CategoryRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 네이버 지도 글자로 리뷰를 쓰려 할 때 장소를 찾거나 만드는 규칙 */
class MapSymbolMatchServiceCreatePlaceTest {

    private static final double DAEDONG_LATITUDE = 37.5790;
    private static final double DAEDONG_LONGITUDE = 126.9240;

    private final CategoryGroup food = CategoryGroup.of("food", "식사", 1);
    private final Category daedong = Category.ofChip("daedong", food, "대동명지도", null, null, null,
            CategoryResultType.PLACE_LIST, true, 1);
    private final Category daedongCafe = Category.ofSubTab("daedong-cafe", daedong, "카페·디저트", null, 1);
    private final Category printer = Category.ofChip("printer", CategoryGroup.of("convenience", "편의", 3),
            "프린트", null, null, null, CategoryResultType.PLACE_LIST, false, 1);

    private PinRepository pinRepository;
    private CategoryRepository categoryRepository;
    private MapSymbolMatchService service;

    @BeforeEach
    void setUp() {
        pinRepository = mock(PinRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        service = new MapSymbolMatchService(pinRepository, categoryRepository, new DistanceCalculator());
        when(pinRepository.findAllInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        when(pinRepository.save(any(Pin.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findByCode("daedong-cafe")).thenReturn(Optional.of(daedongCafe));
        when(categoryRepository.findByCode("printer")).thenReturn(Optional.of(printer));
    }

    @Test
    @DisplayName("근처에 이름이 비슷한 핀이 있으면 새로 만들지 않고 그 핀을 돌려준다")
    void returnsSimilarPinInsteadOfCreating() {
        Pin existing = Pin.ofExternalPlace("daedong-123", daedongCafe, "엔드베이커리",
                DAEDONG_LATITUDE, DAEDONG_LONGITUDE, null, null, null);
        when(pinRepository.findAllInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(existing));

        SymbolMatchResponse response = service.createPlace(
                new SymbolPlaceCreateRequest("엔드베이커리 명지대점", DAEDONG_LATITUDE, DAEDONG_LONGITUDE, "daedong-cafe"));

        assertThat(response.name()).isEqualTo("엔드베이커리");
        verify(pinRepository, never()).save(any());
    }

    @Test
    @DisplayName("없으면 고른 대동명지도 종류로 장소를 만든다")
    void createsDaedongPlace() {
        SymbolMatchResponse response = service.createPlace(
                new SymbolPlaceCreateRequest(" 엔드베이커리 ", DAEDONG_LATITUDE, DAEDONG_LONGITUDE, "daedong-cafe"));

        assertThat(response.name()).isEqualTo("엔드베이커리");
        assertThat(response.type()).isEqualTo("PLACE");
        verify(pinRepository).save(any(Pin.class));
    }

    @Test
    @DisplayName("대동명지도 하위 종류가 아니면 만들지 않는다")
    void rejectsNonDaedongCategory() {
        assertThatThrownBy(() -> service.createPlace(
                new SymbolPlaceCreateRequest("엔드베이커리", DAEDONG_LATITUDE, DAEDONG_LONGITUDE, "printer")))
                .isInstanceOf(SymbolPlaceInvalidException.class);
        verify(pinRepository, never()).save(any());
    }

    @Test
    @DisplayName("학교에서 2km 넘게 떨어진 곳은 만들지 않는다")
    void rejectsFarPlace() {
        assertThatThrownBy(() -> service.createPlace(
                new SymbolPlaceCreateRequest("강남 빵집", 37.4979, 127.0276, "daedong-cafe")))
                .isInstanceOf(SymbolPlaceInvalidException.class);
        verify(pinRepository, never()).save(any());
    }
}
