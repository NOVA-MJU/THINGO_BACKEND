package nova.mjs.domain.thingo.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.map.dto.BuildingDetailResponse;
import nova.mjs.domain.thingo.map.dto.MapCategoryResponse;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.MapSyncDTO;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.service.MapSearchService;
import nova.mjs.domain.thingo.map.service.MapPinService;
import nova.mjs.domain.thingo.map.service.MapSyncService;
import nova.mjs.domain.thingo.map.service.MapSyncServiceImpl;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
import nova.mjs.domain.thingo.map.support.MapRecommendationRanker;
import nova.mjs.domain.thingo.map.support.OperatingStatusResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명지도 검색 E2E 통합 테스트 (Testcontainers PostgreSQL).
 *
 * 실제 요청 경로를 일회용 실 DB로 검증한다:
 *  1) 구글 시트와 동일한 JSON을 동기화(MapSyncService)로 DB에 적재
 *  2) 적재한 데이터가 실제로 잘 들어갔는지(개수/필드 값) 확인
 *  3) 명지도 검색(MapSearchService)이 부분일치/초성/오타/카테고리/필터/운영상태 상속을 올바르게 처리하는지 확인
 *
 * DB 전문검색 확장(pg_trgm) 없이 메모리 스코어링으로 동작하므로 순정 PostgreSQL 컨테이너로 통과한다.
 */
@Testcontainers
@DataJpaTest(properties = "spring.main.allow-bean-definition-overriding=true", excludeAutoConfiguration = {
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
@EnableAutoConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MapSyncServiceImpl.class,
        MapSearchService.class,
        MapPinService.class,
        MapSearchMatcher.class,
        MapRecommendationRanker.class,
        DistanceCalculator.class,
        OperatingStatusResolver.class,
        CampusArea.class,
        // 검색 기능의 JPA 엔티티 리스너가 이 빈을 요구한다(맵 테스트에서 발화하지는 않음).
        SearchIndexPublisher.class
})
class MapSearchE2EIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.database", () -> "postgresql");
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    @Autowired MapSyncService mapSyncService;
    @Autowired MapSearchService mapSearchService;
    @Autowired MapPinService mapPinService;
    @Autowired PinRepository pinRepository;
    @PersistenceContext EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 검색 시나리오용 시트 페이로드 (내부·외부 혼합 라벨과 중복 내부 시설 포함) */
    private static final String SHEET_JSON = """
            {
              "groups": [
                {"code":"food","name":"식사 (F&B)","displayOrder":1},
                {"code":"guide","name":"건물·이동 (Map Guide)","displayOrder":2},
                {"code":"convenience","name":"편의 (Convenience)","displayOrder":3}
              ],
              "categories": [
                {"code":"daedong","groupCode":"food","label":"대동명지도","iconKey":"MyeongwolIcon","resultType":"PLACE_LIST","quickMenu":true,"displayOrder":1},
                {"code":"korean","groupCode":"food","parentCode":"daedong","label":"한식","iconKey":"KoreanFoodIcon","displayOrder":1},
                {"code":"cafe","groupCode":"food","label":"카페","iconKey":"CafeIcon","resultType":"PLACE_LIST","quickMenu":true,"displayOrder":2},
                {"code":"bar","groupCode":"food","label":"주점","iconKey":"BarIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":3},
                {"code":"building","groupCode":"guide","label":"건물","iconKey":"BuildingIcon","resultType":"BUILDING_LIST","quickMenu":true,"displayOrder":1},
                {"code":"classroom","groupCode":"guide","label":"강의실","iconKey":"ClassroomIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":2},
                {"code":"printer","groupCode":"convenience","label":"프린터","iconKey":"PrinterIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":1},
                {"code":"restroom","groupCode":"convenience","label":"화장실","iconKey":"RestroomIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":2}
              ],
              "buildings": [
                {"code":"b-main","categoryCode":"building","name":"종합관","latitude":37.5803,"longitude":126.9223,"imageUrl":"https://thingo.kr/b1.jpg","infoText":"구 본관","buildingNumber":1,"classroomCode":"S1XXX"}
              ],
              "floors": [
                {"buildingCode":"b-main","label":"F1","floorOrder":1,"mapImageUrl":"https://thingo.kr/f1.jpg"},
                {"buildingCode":"b-main","label":"F3","floorOrder":3,"mapImageUrl":"https://thingo.kr/f3.jpg"}
              ],
              "places": [
                {"code":"p-happy","categoryCode":"korean","name":"행복식당","latitude":37.5805,"longitude":126.9230,"address":"서울 서대문구 거북골로 34","infoText":"현금만"},
                {"code":"p-twosome","categoryCode":"cafe","name":"투썸플레이스 명지대점","latitude":37.5806,"longitude":126.9231,"address":"서울 서대문구 거북골로 31-1 1~3층","infoText":"콘센트 많음"},
                {"code":"p-printer","categoryCode":"printer","name":"무한프린터","parentBuildingCode":"b-main","floorLabel":"F1","infoText":"흑백 50원"},
                {"code":"p-printer-outside","categoryCode":"printer","name":"명지문구 프린터","latitude":37.5810,"longitude":126.9240,"address":"서울 서대문구 명지대길 10"},
                {"code":"p-restroom-f1-east","categoryCode":"restroom","name":"화장실","parentBuildingCode":"b-main","floorLabel":"F1"},
                {"code":"p-restroom-f3-west","categoryCode":"restroom","name":"화장실","parentBuildingCode":"b-main","floorLabel":"F3"},
                {"code":"p-s1353","categoryCode":"classroom","name":"강의실 S1353","parentBuildingCode":"b-main","floorLabel":"F3","indoorCode":"S1353"},
                {"code":"p-s1350","categoryCode":"classroom","name":"강의실 S1350","parentBuildingCode":"b-main","floorLabel":"F3","indoorCode":"S1350"},
                {"code":"p-twodari","categoryCode":"bar","name":"투다리 하나로점","latitude":37.5812,"longitude":126.9250,"address":"서울 서대문구 명지대길 18","infoText":"단체석 있음"}
              ],
              "operatingHours": [
                {"buildingCode":"b-main","dayOfWeek":"MONDAY","openTime":"09:00","closeTime":"18:00"},
                {"buildingCode":"b-main","dayOfWeek":"SUNDAY","closed":true}
              ]
            }
            """;

    /** 시트 적재 + 영속성 컨텍스트 비우기 (조회가 DB를 재조회하도록) */
    private void syncAndClear() throws Exception {
        mapSyncService.syncFromSheet(objectMapper.readValue(SHEET_JSON, MapSyncDTO.SyncRequest.class));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("동기화한 명지도 데이터가 실제로 DB에 잘 적재된다 (개수/필드값)")
    void should_persistSheetData_correctly() throws Exception {
        // given - when
        MapSyncDTO.SyncResult result = mapSyncService.syncFromSheet(
                objectMapper.readValue(SHEET_JSON, MapSyncDTO.SyncRequest.class));
        entityManager.flush();
        entityManager.clear();

        // then - 섹션별 처리 건수
        assertThat(result.getGroups()).isEqualTo(3);
        assertThat(result.getCategories()).isEqualTo(8);
        assertThat(result.getBuildings()).isEqualTo(1);
        assertThat(result.getFloors()).isEqualTo(2);
        assertThat(result.getPlaces()).isEqualTo(9);
        assertThat(result.getOperatingHours()).isEqualTo(2);

        // 핀 총 10개 (건물1 + 장소9)
        assertThat(pinRepository.count()).isEqualTo(10);

        // 개별 핀의 내용이 시트 값 그대로 적재됐는지 (데이터 품질)
        Pin twosome = pinRepository.findByCode("p-twosome").orElseThrow();
        assertThat(twosome.getName()).isEqualTo("투썸플레이스 명지대점");
        assertThat(twosome.getType()).isEqualTo(PinType.PLACE);
        assertThat(twosome.getAddress()).isEqualTo("서울 서대문구 거북골로 31-1 1~3층");
        assertThat(twosome.getCategory().getCode()).isEqualTo("cafe");

        Pin building = pinRepository.findByCode("b-main").orElseThrow();
        assertThat(building.getType()).isEqualTo(PinType.BUILDING);
        assertThat(building.getClassroomCode()).isEqualTo("S1XXX");
    }

    @Test
    @DisplayName("부분일치: '종합'으로 종합관을 찾는다")
    void should_findByPartialName() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = search("종합");

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("종합관");
        assertThat(results).allSatisfy(r -> assertThat(r.getType()).isEqualTo("BUILDING"));
    }

    @Test
    @DisplayName("초성: 'ㅌㅆ'로 투썸플레이스를 찾는다")
    void should_findByChosung() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = search("ㅌㅆ");

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("투썸플레이스 명지대점");
    }

    @Test
    @DisplayName("오타: 3글자 이상 검색어('투썹플레이스')는 오타가 있어도 투썸플레이스를 찾는다")
    void should_findDespiteTypo() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = search("투썹플레이스");

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("투썸플레이스 명지대점");
    }

    @Test
    @DisplayName("회귀: '투썸'(2글자) 검색은 첫 글자만 같은 무관한 장소(투다리)를 오매칭하지 않는다")
    void should_notMatchUnrelatedPlace_when_searchingShortAbbreviation() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = search("투썸");

        // then - 카페(투썸플레이스)만 나오고, 이름이 다른 '투다리 하나로점'은 섞이지 않는다
        assertThat(results).extracting(PinSummaryResponse::getName)
                .contains("투썸플레이스 명지대점")
                .doesNotContain("투다리 하나로점");
    }

    @Test
    @DisplayName("카테고리명: '한식'으로 한식 카테고리 장소(행복식당)를 찾는다")
    void should_findByCategoryLabel() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = search("한식");

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("행복식당");
    }

    @Test
    @DisplayName("운영상태: 내부 장소는 소속 건물 운영시간을 상속하고, 외부 장소는 미표시")
    void should_inheritOperatingStatus_forInternalPlace() throws Exception {
        // given
        syncAndClear();

        // when
        PinSummaryResponse internalPrinter = only(search("무한프린터"));
        PinSummaryResponse externalCafe = only(search("투썸플레이스 명지대점"));
        PinSummaryResponse mainBuilding = only(search("종합관"));

        // then
        assertThat(internalPrinter.getOperatingStatus()).isNotNull();   // 소속 건물(종합관) 운영시간 상속
        assertThat(externalCafe.getOperatingStatus()).isNull();         // 외부 장소, 운영시간 없음
        assertThat(mainBuilding.getOperatingStatus()).isNotNull();      // 건물 자체 운영시간
    }

    @Test
    @DisplayName("내부 장소 위치는 건물명+층수로 표시된다")
    void should_showInternalPlaceLocation() throws Exception {
        // given
        syncAndClear();

        // when
        PinSummaryResponse printer = only(search("무한프린터"));

        // then
        assertThat(printer.getLocation()).isEqualTo("종합관 F1");
    }

    @Test
    @DisplayName("정확한 호실 코드로 검색하면 층 안내도 링크 한 건을 반환한다")
    void should_returnFloorMapLink_when_exactIndoorCodeMatches() throws Exception {
        syncAndClear();

        List<PinSummaryResponse> response = search("s-1353");
        assertThat(response).hasSize(1);

        PinSummaryResponse room = response.get(0);
        assertThat(room.getId()).isNotNull();
        assertThat(room.getType()).isEqualTo("FLOOR_MAP");
        assertThat(room.getName()).isEqualTo("강의실 S1353");
        assertThat(room.getLink())
                .contains("/maps/floor?buildingId=", "floorLabel=F3", "placeId=" + room.getId(), "target=S1353")
                .doesNotContain("pinId=", "xPercent", "yPercent");
    }

    @Test
    @DisplayName("라벨명이 장소명과 겹쳐도 라벨 목록을 우선한다")
    void should_prioritizeLabel_when_labelMatchesExactly() throws Exception {
        syncAndClear();

        List<PinSummaryResponse> response = search("카페");

        assertThat(response).extracting(PinSummaryResponse::getName)
                .containsExactly("투썸플레이스 명지대점");
    }

    @Test
    @DisplayName("프린터 라벨 검색은 내부 FLOOR_MAP과 외부 PLACE를 한 목록에 반환한다")
    void should_mixInternalAndExternalPlaces_inLabelResults() throws Exception {
        syncAndClear();

        List<PinSummaryResponse> results = search("프린터");

        PinSummaryResponse internal = resultNamed(results, "무한프린터");
        PinSummaryResponse external = resultNamed(results, "명지문구 프린터");
        assertThat(internal.getType()).isEqualTo("FLOOR_MAP");
        assertThat(internal.getLink()).contains("placeId=" + internal.getId(), "target=")
                .doesNotContain("p-printer");
        assertThat(external.getType()).isEqualTo("PLACE");
        assertThat(external.getLink()).isNull();
    }

    @Test
    @DisplayName("중복 내부 시설은 각각 고유 placeId를 가진 층별안내도 목록으로 반환한다")
    void should_returnDistinctFloorMapTargets_forDuplicateFacilities() throws Exception {
        syncAndClear();

        List<PinSummaryResponse> results = search("화장실");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(PinSummaryResponse::getType)
                .containsOnly("FLOOR_MAP");
        assertThat(results).allSatisfy(result ->
                assertThat(result.getLink()).contains("placeId=" + result.getId()));
        assertThat(results).extracting(PinSummaryResponse::getLink).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("floorMap=true면 칩 목록이 내부 시설만 반환한다 (외부 장소 제외)")
    void should_returnInternalOnly_when_floorMapTrue() throws Exception {
        syncAndClear();

        // 프린터: 내부(무한프린터, 종합관 F1) + 외부(명지문구 프린터)가 섞여 있음
        List<PinSummaryResponse> floorMapMode = mapPinService.getPinsByCategory(
                "printer", null, null, 0, 20, null, null, true);
        List<PinSummaryResponse> defaultMode = mapPinService.getPinsByCategory(
                "printer", null, null, 0, 20, null, null, false);

        // floorMap=true: 내부 시설만, 전부 FLOOR_MAP
        assertThat(floorMapMode).extracting(PinSummaryResponse::getName)
                .contains("무한프린터")
                .doesNotContain("명지문구 프린터");
        assertThat(floorMapMode).allSatisfy(r -> assertThat(r.getType()).isEqualTo("FLOOR_MAP"));

        // 기본(false): 내부·외부 모두 포함
        assertThat(defaultMode).extracting(PinSummaryResponse::getName)
                .contains("무한프린터", "명지문구 프린터");
    }

    @Test
    @DisplayName("자동완성: '투'로 투썸플레이스 이름을 제안한다")
    void should_suggestNames() throws Exception {
        // given
        syncAndClear();

        // when
        List<MapSuggestResponse> suggestions = mapSearchService.suggest("투", 10);

        // then
        assertThat(suggestions).extracting(MapSuggestResponse::getName).contains("투썸플레이스 명지대점");
        assertThat(suggestions).allSatisfy(s -> assertThat(s.getId()).isNotNull());
    }

    @Test
    @DisplayName("자동완성의 내부 장소도 FLOOR_MAP 링크를 반환한다")
    void should_suggestFloorMapRoute_forInternalPlace() throws Exception {
        syncAndClear();

        MapSuggestResponse suggestion = mapSearchService.suggest("S1353", 10).get(0);

        assertThat(suggestion.getType()).isEqualTo("FLOOR_MAP");
        assertThat(suggestion.getLink()).contains("floorLabel=F3", "placeId=" + suggestion.getId(), "target=S1353");
    }

    @Test
    @DisplayName("강의실(classroom)은 검색 전용이라 칩 목록·건물 탭·층별 시설 목록에 나오지 않는다")
    void should_hideFloorPlanOnlyCategory_fromChipsAndBuildingDetail() throws Exception {
        syncAndClear();
        Long buildingId = pinRepository.findByCode("b-main").orElseThrow().getId();

        assertThat(mapPinService.getCategories())
                .flatExtracting(MapCategoryResponse::getChips)
                .extracting(MapCategoryResponse.Chip::getCode)
                .doesNotContain("classroom");
        assertThat(mapPinService.getBuildingCategoryTabs(buildingId))
                .extracting(BuildingDetailResponse.CategoryTab::getCode)
                .contains("printer")
                .doesNotContain("classroom");
        assertThat(mapPinService.getBuildingDetail(buildingId, null, null, null).getFloors())
                .flatExtracting(BuildingDetailResponse.FloorPlaces::getPlaces)
                .extracting(BuildingDetailResponse.PlaceBrief::getName)
                .doesNotContain("강의실 S1353");

        // 검색으로는 여전히 찾을 수 있다
        assertThat(search("S1353")).extracting(PinSummaryResponse::getName).containsExactly("강의실 S1353");
    }

    @Test
    @DisplayName("S 없이 호수만 입력해도(1353) 유사 호실 없이 정확히 그 호실 하나만 검색·자동완성한다")
    void should_returnOnlyExactRoom_when_roomNumberWithoutPrefix() throws Exception {
        syncAndClear();

        assertThat(search("1353")).extracting(PinSummaryResponse::getIndoorCode).containsExactly("S1353");
        assertThat(mapSearchService.suggest("1353", 10)).extracting(MapSuggestResponse::getIndoorCode)
                .containsExactly("S1353");
        // 코드를 다 치지 않은 중간 입력은 유사 호실 목록을 유지한다
        assertThat(mapSearchService.suggest("135", 10)).extracting(MapSuggestResponse::getIndoorCode)
                .contains("S1353", "S1350");
    }

    @Test
    @DisplayName("빈 검색어는 빈 목록을 반환한다")
    void should_returnEmpty_when_blankKeyword() throws Exception {
        // given
        syncAndClear();

        // when - then
        List<PinSummaryResponse> response = search("  ");
        assertThat(response).isEmpty();
        assertThat(mapSearchService.suggest("", 10)).isEmpty();
    }

    private List<PinSummaryResponse> search(String keyword) {
        return mapSearchService.search(keyword, null, null, 0, 20, null, null);
    }

    /** 검색 결과에서 특정 핀 1건을 뽑는다 (이름이 유니크한 검색어 전제) */
    private PinSummaryResponse only(List<PinSummaryResponse> results) {
        assertThat(results).isNotEmpty();
        return results.get(0);
    }

    private PinSummaryResponse resultNamed(List<PinSummaryResponse> results, String name) {
        return results.stream()
                .filter(result -> result.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
