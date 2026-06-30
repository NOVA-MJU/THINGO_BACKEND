package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.dto.MapSyncDTO;
import nova.mjs.domain.thingo.map.entity.*;
import nova.mjs.domain.thingo.map.exception.MapSyncException;
import nova.mjs.domain.thingo.map.repository.*;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 구글 시트 → DB 명지도 동기화 구현.
 *
 * 비즈니스 흐름 (의존성 순서로 처리, 실패 시 전체 롤백):
 *  1) 그룹 → 2) 카테고리(최상위 먼저, 하위 탭 나중) → 3) 건물 → 4) 층 → 5) 장소 → 6) 운영시간
 *
 * 모든 항목은 code(그룹/카테고리/핀) 또는 (건물+라벨/요일) 키로 upsert 한다.
 * 즐겨찾기(PinFavorite)가 Pin을 참조하므로 '전체 삭제 후 재삽입' 대신 upsert로 핀을 보존한다.
 * (v1은 추가/수정만 수행하고, 시트에서 빠진 행의 자동 삭제는 하지 않는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapSyncServiceImpl implements MapSyncService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final CategoryRepository categoryRepository;
    private final PinRepository pinRepository;
    private final FloorRepository floorRepository;
    private final OperatingHourRepository operatingHourRepository;

    @Override
    @Transactional
    public MapSyncDTO.SyncResult syncFromSheet(MapSyncDTO.SyncRequest request) {
        // 같은 요청 안에서 방금 만든 항목을 다시 참조하기 위한 요청 단위 캐시 (스레드 안전)
        SyncContext context = new SyncContext();

        int groups = upsertGroups(request.getGroups(), context);
        int categories = upsertCategories(request.getCategories(), context);
        int buildings = upsertBuildings(request.getBuildings(), context);
        int floors = upsertFloors(request.getFloors(), context);
        int places = upsertPlaces(request.getPlaces(), context);
        int operatingHours = upsertOperatingHours(request.getOperatingHours(), context);

        log.info("[명지도 동기화 완료] groups={}, categories={}, buildings={}, floors={}, places={}, operatingHours={}",
                groups, categories, buildings, floors, places, operatingHours);

        return MapSyncDTO.SyncResult.builder()
                .groups(groups)
                .categories(categories)
                .buildings(buildings)
                .floors(floors)
                .places(places)
                .operatingHours(operatingHours)
                .build();
    }

    /** 요청 단위 참조 캐시 (방금 upsert한 항목을 DB 재조회 없이 참조) */
    private static class SyncContext {
        final Map<String, CategoryGroup> groups = new HashMap<>();
        final Map<String, Category> categories = new HashMap<>();
        final Map<String, Pin> pins = new HashMap<>();
        final Map<String, Floor> floors = new HashMap<>();
    }

    // ====================== 1. 그룹 ======================

    private int upsertGroups(List<MapSyncDTO.GroupRow> rows, SyncContext context) {
        for (int i = 0; i < rows.size(); i++) {
            MapSyncDTO.GroupRow row = rows.get(i);
            requireText(row.getCode(), "category_groups", i, "code");
            requireText(row.getName(), "category_groups", i, "name");
            int displayOrder = row.getDisplayOrder() != null ? row.getDisplayOrder() : 0;

            CategoryGroup group = categoryGroupRepository.findByCode(row.getCode())
                    .map(existing -> {
                        existing.update(row.getName(), displayOrder);
                        return existing;
                    })
                    .orElseGet(() -> categoryGroupRepository.save(
                            CategoryGroup.of(row.getCode(), row.getName(), displayOrder)));
            context.groups.put(group.getCode(), group);
        }
        return rows.size();
    }

    // ====================== 2. 카테고리 ======================

    private int upsertCategories(List<MapSyncDTO.CategoryRow> rows, SyncContext context) {
        // 부모-자식 의존 때문에 최상위 칩(parentCode 없음)을 먼저 처리한다
        for (int i = 0; i < rows.size(); i++) {
            if (isBlank(rows.get(i).getParentCode())) {
                upsertCategory(rows.get(i), i, context);
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            if (!isBlank(rows.get(i).getParentCode())) {
                upsertCategory(rows.get(i), i, context);
            }
        }
        return rows.size();
    }

    private void upsertCategory(MapSyncDTO.CategoryRow row, int index, SyncContext context) {
        requireText(row.getCode(), "categories", index, "code");
        requireText(row.getGroupCode(), "categories", index, "group_code");
        requireText(row.getLabel(), "categories", index, "label");

        CategoryGroup group = resolveGroup(row.getGroupCode(), index, context);
        Category parent = isBlank(row.getParentCode()) ? null : resolveCategory(row.getParentCode(), index, context);
        // 하위 탭은 결과 종류를 부모에게서 상속한다(ofSubTab 규칙). 최상위 칩만 result_type을 직접 요구한다.
        CategoryResultType resultType = parent != null ? parent.getResultType() : parseResultType(row.getResultType(), index);
        boolean quickMenu = Boolean.TRUE.equals(row.getQuickMenu());
        int displayOrder = row.getDisplayOrder() != null ? row.getDisplayOrder() : 0;

        Category category = categoryRepository.findByCode(row.getCode())
                .map(existing -> {
                    existing.update(group, parent, row.getLabel(), row.getSubtitle(), row.getTooltipText(),
                            row.getIconKey(), resultType, quickMenu, displayOrder);
                    return existing;
                })
                .orElseGet(() -> {
                    Category created = parent == null
                            ? Category.ofChip(row.getCode(), group, row.getLabel(), row.getSubtitle(),
                                row.getTooltipText(), row.getIconKey(), resultType, quickMenu, displayOrder)
                            : Category.ofSubTab(row.getCode(), parent, row.getLabel(), row.getIconKey(), displayOrder);
                    return categoryRepository.save(created);
                });
        context.categories.put(category.getCode(), category);
    }

    // ====================== 3. 건물 ======================

    private int upsertBuildings(List<MapSyncDTO.BuildingRow> rows, SyncContext context) {
        for (int i = 0; i < rows.size(); i++) {
            MapSyncDTO.BuildingRow row = rows.get(i);
            requireText(row.getCode(), "buildings", i, "code");
            requireText(row.getCategoryCode(), "buildings", i, "category_code");
            requireText(row.getName(), "buildings", i, "name");

            Category category = resolveCategory(row.getCategoryCode(), i, context);

            Pin building = pinRepository.findByCode(row.getCode())
                    .map(existing -> {
                        existing.update(category, row.getName(), row.getLatitude(), row.getLongitude(),
                                row.getImageUrl(), row.getInfoText(), row.getBuildingNumber(), row.getClassroomCode(),
                                null, null, null);
                        return existing;
                    })
                    .orElseGet(() -> pinRepository.save(Pin.ofBuilding(row.getCode(), category, row.getName(),
                            row.getLatitude(), row.getLongitude(), row.getImageUrl(), row.getInfoText(),
                            row.getBuildingNumber(), row.getClassroomCode())));
            context.pins.put(building.getCode(), building);
        }
        return rows.size();
    }

    // ====================== 4. 층 ======================

    private int upsertFloors(List<MapSyncDTO.FloorRow> rows, SyncContext context) {
        for (int i = 0; i < rows.size(); i++) {
            MapSyncDTO.FloorRow row = rows.get(i);
            requireText(row.getBuildingCode(), "floors", i, "building_code");
            requireText(row.getLabel(), "floors", i, "label");

            Pin building = resolveBuilding(row.getBuildingCode(), i, context);
            int floorOrder = row.getFloorOrder() != null ? row.getFloorOrder() : 0;

            Floor floor = floorRepository.findByBuildingAndLabel(building, row.getLabel())
                    .map(existing -> {
                        existing.update(floorOrder, row.getMapImageUrl());
                        return existing;
                    })
                    .orElseGet(() -> floorRepository.save(
                            Floor.of(building, row.getLabel(), floorOrder, row.getMapImageUrl())));
            context.floors.put(floorKey(row.getBuildingCode(), row.getLabel()), floor);
        }
        return rows.size();
    }

    // ====================== 5. 장소 ======================

    private int upsertPlaces(List<MapSyncDTO.PlaceRow> rows, SyncContext context) {
        for (int i = 0; i < rows.size(); i++) {
            MapSyncDTO.PlaceRow row = rows.get(i);
            requireText(row.getCode(), "places", i, "code");
            requireText(row.getCategoryCode(), "places", i, "category_code");
            requireText(row.getName(), "places", i, "name");

            Category category = resolveCategory(row.getCategoryCode(), i, context);
            Pin parentBuilding = isBlank(row.getParentBuildingCode())
                    ? null : resolveBuilding(row.getParentBuildingCode(), i, context);
            Floor floor = (parentBuilding != null && !isBlank(row.getFloorLabel()))
                    ? resolveFloor(row.getParentBuildingCode(), row.getFloorLabel(), i, context) : null;

            Pin place = pinRepository.findByCode(row.getCode())
                    .map(existing -> {
                        existing.update(category, row.getName(), row.getLatitude(), row.getLongitude(),
                                row.getImageUrl(), row.getInfoText(), null, null,
                                row.getAddress(), parentBuilding, floor);
                        return existing;
                    })
                    .orElseGet(() -> {
                        Pin created = parentBuilding != null
                                ? Pin.ofInternalPlace(row.getCode(), category, row.getName(),
                                    row.getImageUrl(), row.getInfoText(), parentBuilding, floor)
                                : Pin.ofExternalPlace(row.getCode(), category, row.getName(),
                                    row.getLatitude(), row.getLongitude(), row.getImageUrl(),
                                    row.getInfoText(), row.getAddress());
                        return pinRepository.save(created);
                    });
            context.pins.put(place.getCode(), place);
        }
        return rows.size();
    }

    // ====================== 6. 운영시간 ======================

    private int upsertOperatingHours(List<MapSyncDTO.OperatingHourRow> rows, SyncContext context) {
        for (int i = 0; i < rows.size(); i++) {
            MapSyncDTO.OperatingHourRow row = rows.get(i);
            requireText(row.getBuildingCode(), "operating_hours", i, "building_code");

            Pin building = resolveBuilding(row.getBuildingCode(), i, context);
            DayOfWeek dayOfWeek = parseDayOfWeek(row.getDayOfWeek(), i);
            boolean always24h = Boolean.TRUE.equals(row.getAlways24h());
            boolean closed = Boolean.TRUE.equals(row.getClosed());
            LocalTime openTime = parseTime(row.getOpenTime(), i);
            LocalTime closeTime = parseTime(row.getCloseTime(), i);

            operatingHourRepository.findByPinAndDayOfWeek(building, dayOfWeek)
                    .ifPresentOrElse(
                            existing -> existing.update(openTime, closeTime, always24h, closed, row.getNote()),
                            () -> operatingHourRepository.save(buildOperatingHour(
                                    building, dayOfWeek, openTime, closeTime, always24h, closed, row.getNote())));
        }
        return rows.size();
    }

    private OperatingHour buildOperatingHour(Pin building, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime,
                                             boolean always24h, boolean closed, String note) {
        if (closed) {
            return OperatingHour.ofClosed(building, dayOfWeek);
        }
        if (always24h) {
            return OperatingHour.ofAlwaysOpen(building, dayOfWeek, note);
        }
        return OperatingHour.ofOpen(building, dayOfWeek, openTime, closeTime, note);
    }

    // ====================== 참조 해석 (캐시 → DB 폴백) ======================

    private CategoryGroup resolveGroup(String code, int index, SyncContext context) {
        CategoryGroup cached = context.groups.get(code);
        if (cached != null) {
            return cached;
        }
        return categoryGroupRepository.findByCode(code)
                .orElseThrow(() -> invalidRow("categories", index, "존재하지 않는 group_code: " + code));
    }

    private Category resolveCategory(String code, int index, SyncContext context) {
        Category cached = context.categories.get(code);
        if (cached != null) {
            return cached;
        }
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> invalidRow("categories/pins", index, "존재하지 않는 category code: " + code));
    }

    private Pin resolveBuilding(String code, int index, SyncContext context) {
        Pin cached = context.pins.get(code);
        Pin pin = cached != null ? cached
                : pinRepository.findByCode(code)
                    .orElseThrow(() -> invalidRow("buildings 참조", index, "존재하지 않는 building code: " + code));
        if (pin.getType() != PinType.BUILDING) {
            throw invalidRow("buildings 참조", index, code + " 는 건물이 아닙니다.");
        }
        return pin;
    }

    private Floor resolveFloor(String buildingCode, String label, int index, SyncContext context) {
        Floor cached = context.floors.get(floorKey(buildingCode, label));
        if (cached != null) {
            return cached;
        }
        Pin building = resolveBuilding(buildingCode, index, context);
        return floorRepository.findByBuildingAndLabel(building, label)
                .orElseThrow(() -> invalidRow("places", index, "존재하지 않는 층: " + buildingCode + " " + label));
    }

    private String floorKey(String buildingCode, String label) {
        return buildingCode + "::" + label;
    }

    // ====================== 검증/파싱 ======================

    private void requireText(String value, String section, int index, String field) {
        if (isBlank(value)) {
            throw invalidRow(section, index, field + " 가 비어 있습니다.");
        }
    }

    private CategoryResultType parseResultType(String value, int index) {
        try {
            return CategoryResultType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalidRow("categories", index, "result_type 값이 올바르지 않습니다. (PLACE_LIST/BUILDING_LIST/BUS)");
        }
    }

    private DayOfWeek parseDayOfWeek(String value, int index) {
        try {
            return DayOfWeek.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalidRow("operating_hours", index, "day_of_week 값이 올바르지 않습니다. (MONDAY ... SUNDAY)");
        }
    }

    private LocalTime parseTime(String value, int index) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw invalidRow("operating_hours", index, "시간 형식이 올바르지 않습니다. (HH:mm) - " + value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private MapSyncException invalidRow(String section, int index, String reason) {
        return new MapSyncException(
                "[" + section + "] " + (index + 1) + "번째 행: " + reason,
                ErrorCode.MAP_SYNC_INVALID_ROW);
    }
}
