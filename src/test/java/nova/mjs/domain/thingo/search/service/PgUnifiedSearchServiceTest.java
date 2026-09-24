package nova.mjs.domain.thingo.search.service;

import nova.mjs.domain.thingo.realtimeKeyword.RealtimeKeywordService;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.Floor;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.search.dto.SearchResponseDTO;
import nova.mjs.domain.thingo.search.dto.SearchResultRow;
import nova.mjs.domain.thingo.search.repository.UnifiedSearchIndexRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PgUnifiedSearchServiceTest {

    @Mock private UnifiedSearchIndexRepository repository;
    @Mock private PinRepository pinRepository;
    @Mock private RealtimeKeywordService realtimeKeywordService;

    @Test
    void exposesFullSourceContentOnlyForAcademicGuides() {
        PgUnifiedSearchService service = new PgUnifiedSearchService(repository, pinRepository, realtimeKeywordService);
        var pageable = PageRequest.of(0, 10);
        var academic = row("ACADEMIC_GUIDE:rule", "ACADEMIC_GUIDE", "학번별 전체 규칙");
        var notice = row("NOTICE:1", "NOTICE", "일반 공지 원문");

        when(realtimeKeywordService.getTopKeywords(10)).thenReturn(List.of());
        when(repository.search(anyString(), isNull(), isNull(), any(), isNull(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(academic, notice), pageable, 2));

        Page<SearchResponseDTO> result = service.search("학기교", null, null, "relevance", pageable);

        assertThat(result.getContent().get(0).getContent()).isEqualTo("학번별 전체 규칙");
        assertThat(result.getContent().get(1).getContent()).isNull();
    }

    @Test
    void exactIndoorCodeReturnsFloorMapBeforeDocumentSearch() {
        PgUnifiedSearchService service = new PgUnifiedSearchService(repository, pinRepository, realtimeKeywordService);
        var pageable = PageRequest.of(0, 10);
        Pin room = org.mockito.Mockito.mock(Pin.class);
        Pin building = org.mockito.Mockito.mock(Pin.class);
        Floor floor = org.mockito.Mockito.mock(Floor.class);
        Category category = org.mockito.Mockito.mock(Category.class);

        when(pinRepository.findByIndoorCodeIgnoreCase("S1353")).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(353L);
        when(room.getType()).thenReturn(PinType.PLACE);
        when(room.getIndoorCode()).thenReturn("S1353");
        when(room.getName()).thenReturn("강의실 S1353");
        when(room.getParentBuilding()).thenReturn(building);
        when(room.getFloor()).thenReturn(floor);
        when(room.getCategory()).thenReturn(category);
        when(building.getId()).thenReturn(2L);
        when(building.getName()).thenReturn("학생회관");
        when(floor.getLabel()).thenReturn("F4");
        when(category.getCode()).thenReturn("classroom");

        Page<SearchResponseDTO> result = service.search("s-1353", null, null, "relevance", pageable);

        SearchResponseDTO item = result.getContent().get(0);
        assertThat(item.getType()).isEqualTo("map");
        assertThat(item.getLink()).isEqualTo("/maps/floor?buildingId=2&floorLabel=F4&placeId=353&target=S1353");
        verifyNoInteractions(repository);
    }

    private SearchResultRow row(String id, String type, String content) {
        return new SearchResultRow(
                id, id, type, "academic_rule", "제목", "제목", content, "검색 조각",
                "명지대학교", "https://example.com/" + id, null, null, null,
                Instant.parse("2026-08-04T00:00:00Z"), List.of(), List.of(), 1.0
        );
    }
}
