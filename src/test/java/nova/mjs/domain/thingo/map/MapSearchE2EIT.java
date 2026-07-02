package nova.mjs.domain.thingo.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.map.dto.MapSuggestResponse;
import nova.mjs.domain.thingo.map.dto.MapSyncDTO;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinType;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.service.MapSearchService;
import nova.mjs.domain.thingo.map.service.MapSyncService;
import nova.mjs.domain.thingo.map.service.MapSyncServiceImpl;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
import nova.mjs.domain.thingo.map.support.MapSearchMatcher;
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
        MapSearchMatcher.class,
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
    @Autowired PinRepository pinRepository;
    @PersistenceContext EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 검색 시나리오용 시트 페이로드 (건물 1 + 외부장소 2 + 내부장소 1) */
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
                {"code":"building","groupCode":"guide","label":"건물","iconKey":"BuildingIcon","resultType":"BUILDING_LIST","quickMenu":true,"displayOrder":1},
                {"code":"printer","groupCode":"convenience","label":"프린터","iconKey":"PrinterIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":1}
              ],
              "buildings": [
                {"code":"b-main","categoryCode":"building","name":"종합관","latitude":37.5803,"longitude":126.9223,"imageUrl":"https://thingo.kr/b1.jpg","infoText":"구 본관","buildingNumber":1,"classroomCode":"S1XXX"}
              ],
              "floors": [
                {"buildingCode":"b-main","label":"F1","floorOrder":1,"mapImageUrl":"https://thingo.kr/f1.jpg"}
              ],
              "places": [
                {"code":"p-happy","categoryCode":"korean","name":"행복식당","latitude":37.5805,"longitude":126.9230,"address":"서울 서대문구 거북골로 34","infoText":"현금만"},
                {"code":"p-twosome","categoryCode":"cafe","name":"투썸플레이스 명지대점","latitude":37.5806,"longitude":126.9231,"address":"서울 서대문구 거북골로 31-1 1~3층","infoText":"콘센트 많음"},
                {"code":"p-printer","categoryCode":"printer","name":"무한프린터","parentBuildingCode":"b-main","floorLabel":"F1","infoText":"흑백 50원"}
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
        assertThat(result.getCategories()).isEqualTo(5);
        assertThat(result.getBuildings()).isEqualTo(1);
        assertThat(result.getFloors()).isEqualTo(1);
        assertThat(result.getPlaces()).isEqualTo(3);
        assertThat(result.getOperatingHours()).isEqualTo(2);

        // 핀 총 4개 (건물1 + 장소3)
        assertThat(pinRepository.count()).isEqualTo(4);

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
        List<PinSummaryResponse> results = mapSearchService.search("종합", null, null, null, 0, 20, null);

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
        List<PinSummaryResponse> results = mapSearchService.search("ㅌㅆ", null, null, null, 0, 20, null);

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("투썸플레이스 명지대점");
    }

    @Test
    @DisplayName("오타: '투썹'으로도 투썸플레이스를 찾는다")
    void should_findDespiteTypo() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = mapSearchService.search("투썹", null, null, null, 0, 20, null);

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("투썸플레이스 명지대점");
    }

    @Test
    @DisplayName("카테고리명: '한식'으로 한식 카테고리 장소(행복식당)를 찾는다")
    void should_findByCategoryLabel() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> results = mapSearchService.search("한식", null, null, null, 0, 20, null);

        // then
        assertThat(results).extracting(PinSummaryResponse::getName).contains("행복식당");
    }

    @Test
    @DisplayName("종류 필터: type=BUILDING/PLACE로 결과를 걸러낸다")
    void should_filterByType() throws Exception {
        // given
        syncAndClear();

        // when
        List<PinSummaryResponse> twosomeAsBuilding = mapSearchService.search("투썸", PinType.BUILDING, null, null, 0, 20, null);
        List<PinSummaryResponse> twosomeAsPlace = mapSearchService.search("투썸", PinType.PLACE, null, null, 0, 20, null);
        List<PinSummaryResponse> jonghapAsBuilding = mapSearchService.search("종합", PinType.BUILDING, null, null, 0, 20, null);

        // then - 투썸은 장소라 BUILDING 필터에선 안 나오고 PLACE 필터에선 나온다
        assertThat(twosomeAsBuilding).isEmpty();
        assertThat(twosomeAsPlace).extracting(PinSummaryResponse::getName).contains("투썸플레이스 명지대점");
        assertThat(jonghapAsBuilding).extracting(PinSummaryResponse::getName).containsExactly("종합관");
    }

    @Test
    @DisplayName("운영상태: 내부 장소는 소속 건물 운영시간을 상속하고, 외부 장소는 미표시")
    void should_inheritOperatingStatus_forInternalPlace() throws Exception {
        // given
        syncAndClear();

        // when
        PinSummaryResponse internalPrinter = only(mapSearchService.search("무한프린터", null, null, null, 0, 20, null));
        PinSummaryResponse externalCafe = only(mapSearchService.search("투썸플레이스 명지대점", null, null, null, 0, 20, null));
        PinSummaryResponse mainBuilding = only(mapSearchService.search("종합관", null, null, null, 0, 20, null));

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
        PinSummaryResponse printer = only(mapSearchService.search("무한프린터", null, null, null, 0, 20, null));

        // then
        assertThat(printer.getLocation()).isEqualTo("종합관 F1");
    }

    @Test
    @DisplayName("자동완성: '투'로 투썸플레이스 이름을 제안한다")
    void should_suggestNames() throws Exception {
        // given
        syncAndClear();

        // when
        List<MapSuggestResponse> suggestions = mapSearchService.suggest("투", null, 10);

        // then
        assertThat(suggestions).extracting(MapSuggestResponse::getName).contains("투썸플레이스 명지대점");
        assertThat(suggestions).allSatisfy(s -> assertThat(s.getId()).isNotNull());
    }

    @Test
    @DisplayName("빈 검색어는 빈 목록을 반환한다")
    void should_returnEmpty_when_blankKeyword() throws Exception {
        // given
        syncAndClear();

        // when - then
        assertThat(mapSearchService.search("  ", null, null, null, 0, 20, null)).isEmpty();
        assertThat(mapSearchService.suggest("", null, 10)).isEmpty();
    }

    /** 검색 결과에서 특정 핀 1건을 뽑는다 (이름이 유니크한 검색어 전제) */
    private PinSummaryResponse only(List<PinSummaryResponse> results) {
        assertThat(results).isNotEmpty();
        return results.get(0);
    }
}
