package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.map.dto.MapCategoryResponse;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.repository.CategoryGroupRepository;
import nova.mjs.domain.thingo.map.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 카테고리(칩) 조회 서비스.
 *
 * - 상단 퀵메뉴 칩 목록
 * - '더보기' 전체 카테고리 바텀시트 (그룹 + 칩)
 * 둘 다 같은 Category 엔티티를 다른 묶음으로 보여줄 뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryGroupRepository categoryGroupRepository;

    /**
     * 메인 홈 상단 퀵메뉴 칩 목록을 노출 순서대로 반환한다.
     * (quickMenu=true 인 최상위 칩만)
     */
    public List<MapCategoryResponse.Chip> getQuickChips() {
        return categoryRepository.findByQuickMenuTrueAndParentIsNullOrderByDisplayOrderAsc().stream()
                .map(MapCategoryResponse.Chip::from)
                .toList();
    }

    /**
     * '더보기'에서 보여줄 전체 카테고리를 그룹별로 묶어 반환한다.
     *
     * 1) 그룹을 노출 순서대로 조회
     * 2) 최상위 칩 전체를 노출 순서대로 조회
     * 3) 칩을 소속 그룹으로 묶어 그룹 순서대로 조립
     */
    public List<MapCategoryResponse.Group> getAllGroups() {
        List<CategoryGroup> groups = categoryGroupRepository.findAllByOrderByDisplayOrderAsc();
        List<Category> topChips = categoryRepository.findByParentIsNullOrderByDisplayOrderAsc();

        // 칩을 소속 그룹 ID 기준으로 묶는다 (노출 순서는 조회 정렬을 그대로 유지)
        Map<Long, List<MapCategoryResponse.Chip>> chipsByGroupId = topChips.stream()
                .collect(Collectors.groupingBy(
                        chip -> chip.getGroup().getId(),
                        Collectors.mapping(MapCategoryResponse.Chip::from, Collectors.toList())));

        return groups.stream()
                .map(group -> MapCategoryResponse.Group.of(
                        group,
                        chipsByGroupId.getOrDefault(group.getId(), List.of())))
                .toList();
    }
}
