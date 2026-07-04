package nova.mjs.domain.thingo.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import nova.mjs.domain.thingo.map.dto.BuildingDetailResponse;
import nova.mjs.domain.thingo.map.dto.MapSyncDTO;
import nova.mjs.domain.thingo.map.dto.PinSummaryResponse;
import nova.mjs.domain.thingo.map.dto.PlaceDetailResponse;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.map.service.MapPinService;
import nova.mjs.domain.thingo.map.service.MapSyncService;
import nova.mjs.domain.thingo.map.service.MapSyncServiceImpl;
import nova.mjs.domain.thingo.map.service.PinFavoriteService;
import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.map.support.CampusArea;
import nova.mjs.domain.thingo.map.support.DistanceCalculator;
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
 * 명지도 동기화 → 조회 E2E 통합 테스트 (Testcontainers PostgreSQL).
 *
 * 실제 요청 경로를 일회용 실 DB로 검증한다:
 *  1) 구글 시트와 동일한 JSON을 역직렬화해 동기화(MapSyncService)로 DB에 적재
 *  2) 건물/장소 조회 API(MapPinService)가 적재한 데이터를 올바르게 반환하는지 확인
 *
 * 앱 전체 기동(ES/Mongo/Redis)을 피하기 위해 기존 통합테스트와 동일하게 슬라이스 + 자동설정 제외를 사용한다.
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
        MapPinService.class,
        PinFavoriteService.class,
        DistanceCalculator.class,
        OperatingStatusResolver.class,
        CampusArea.class,
        // 검색 기능의 JPA 엔티티 리스너(StudentCouncilNoticeEntityListener 등)가 이 빈을 요구한다.
        // 슬라이스 컨텍스트에서 Hibernate가 리스너를 생성할 때 필요하므로 명시적으로 등록한다(맵 테스트에서 발화하지는 않음).
        SearchIndexPublisher.class
})
class MapSyncE2EIT {

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
    @Autowired MapPinService mapPinService;
    @Autowired PinRepository pinRepository;
    @PersistenceContext EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 구글 시트 한 번 동기화에 해당하는 샘플 페이로드 */
    private static final String SHEET_JSON = """
            {
              "groups": [
                {"code":"food","name":"식사 (F&B)","displayOrder":1},
                {"code":"guide","name":"건물·이동 (Map Guide)","displayOrder":2},
                {"code":"convenience","name":"편의 (Convenience)","displayOrder":3}
              ],
              "categories": [
                {"code":"daedong","groupCode":"food","label":"대동명지도","subtitle":"by. 명월","tooltipText":"명월 Pick 맛집","iconKey":"MyeongwolIcon","resultType":"PLACE_LIST","quickMenu":true,"displayOrder":1},
                {"code":"korean","groupCode":"food","parentCode":"daedong","label":"한식","iconKey":"KoreanFoodIcon","displayOrder":1},
                {"code":"building","groupCode":"guide","label":"건물","iconKey":"BuildingIcon","resultType":"BUILDING_LIST","quickMenu":true,"displayOrder":1},
                {"code":"printer","groupCode":"convenience","label":"프린터","iconKey":"PrinterIcon","resultType":"PLACE_LIST","quickMenu":false,"displayOrder":1}
              ],
              "buildings": [
                {"code":"b-main","categoryCode":"building","name":"종합관","latitude":37.5803,"longitude":126.9223,"imageUrl":"https://thingo.kr/b1.jpg","infoText":"구 본관","buildingNumber":1,"classroomCode":"S1XXX"}
              ],
              "floors": [
                {"buildingCode":"b-main","label":"F1","floorOrder":1,"mapImageUrl":"https://thingo.kr/f1.jpg"},
                {"buildingCode":"b-main","label":"F2","floorOrder":2,"mapImageUrl":"https://thingo.kr/f2.jpg"}
              ],
              "places": [
                {"code":"p-happy","categoryCode":"korean","name":"행복식당","latitude":37.5805,"longitude":126.9230,"address":"서울 서대문구 거북골로 34","infoText":"현금만"},
                {"code":"p-printer","categoryCode":"printer","name":"무한프린터","parentBuildingCode":"b-main","floorLabel":"F1","infoText":"흑백 50원"}
              ],
              "operatingHours": [
                {"buildingCode":"b-main","dayOfWeek":"MONDAY","openTime":"09:00","closeTime":"18:00"},
                {"buildingCode":"b-main","dayOfWeek":"SUNDAY","closed":true}
              ]
            }
            """;

    @Test
    @DisplayName("시트 동기화 후 카테고리/건물/장소 조회가 적재한 데이터를 그대로 반환한다")
    void should_syncSheet_then_serveThroughQueries() throws Exception {
        // given - 시트 JSON을 동기화 요청으로 역직렬화
        MapSyncDTO.SyncRequest request = objectMapper.readValue(SHEET_JSON, MapSyncDTO.SyncRequest.class);

        // when - 동기화 실행 (시트 → DB upsert)
        MapSyncDTO.SyncResult result = mapSyncService.syncFromSheet(request);

        // 적재와 조회를 분리한다: 운영에서 조회는 별도 트랜잭션이라 DB에서 새로 읽는다.
        // 영속성 컨텍스트를 비워 이후 조회가 방금 영속화한 인스턴스의 빈 역방향 컬렉션을 보지 않고 DB를 재조회하게 한다.
        entityManager.flush();
        entityManager.clear();

        // then - 섹션별 처리 건수
        assertThat(result.getGroups()).isEqualTo(3);
        assertThat(result.getCategories()).isEqualTo(4);
        assertThat(result.getBuildings()).isEqualTo(1);
        assertThat(result.getFloors()).isEqualTo(2);
        assertThat(result.getPlaces()).isEqualTo(2);
        assertThat(result.getOperatingHours()).isEqualTo(2);

        // 1) 건물 목록 - 종합관 1개, 강의실코드 노출, GPS 없으니 거리 null
        List<PinSummaryResponse> buildings = mapPinService.getBuildings(null, null, null);
        assertThat(buildings).hasSize(1);
        assertThat(buildings.get(0).getName()).isEqualTo("종합관");
        assertThat(buildings.get(0).getClassroomCode()).isEqualTo("S1XXX");
        assertThat(buildings.get(0).getDistanceMeters()).isNull();
        // 지도 마커용 좌표가 응답에 노출된다
        assertThat(buildings.get(0).getLatitude()).isEqualTo(37.5803);
        assertThat(buildings.get(0).getLongitude()).isEqualTo(126.9223);

        // 2) 칩 클릭(대동명지도) - 하위탭(한식) 장소까지 포함 → 행복식당
        List<PinSummaryResponse> daedongPins = mapPinService.getPinsByCategory("daedong", null, null, 0, 20, null);
        assertThat(daedongPins).extracting(PinSummaryResponse::getName).contains("행복식당");
        assertThat(daedongPins).allSatisfy(p -> assertThat(p.getType()).isEqualTo("PLACE"));

        // 3) 칩 클릭(건물) - BUILDING_LIST → 종합관
        List<PinSummaryResponse> buildingPins = mapPinService.getPinsByCategory("building", null, null, 0, 20, null);
        assertThat(buildingPins).extracting(PinSummaryResponse::getName).containsExactly("종합관");

        // 4) 건물 상세 - 운영시간 2건, 카테고리 탭에 프린터, 층 2개(F1에 무한프린터)
        Pin building = pinRepository.findByCode("b-main").orElseThrow();
        BuildingDetailResponse detail = mapPinService.getBuildingDetail(building.getId(), null, null, null);
        assertThat(detail.getName()).isEqualTo("종합관");
        assertThat(detail.getLatitude()).isEqualTo(37.5803);
        assertThat(detail.getLongitude()).isEqualTo(126.9223);
        assertThat(detail.getWeeklyOperatingHours()).hasSize(2);
        assertThat(detail.getCategoryTabs()).extracting(BuildingDetailResponse.CategoryTab::getCode)
                .containsExactly("printer");
        assertThat(detail.getFloors()).hasSize(2);
        BuildingDetailResponse.FloorPlaces firstFloor = detail.getFloors().stream()
                .filter(f -> "F1".equals(f.getFloorLabel())).findFirst().orElseThrow();
        assertThat(firstFloor.getPlaces()).extracting(BuildingDetailResponse.PlaceBrief::getName)
                .containsExactly("무한프린터");

        // 5) 장소 상세(외부) - 위치는 도로명주소, 운영시간 필드 없음(건물 전용)
        Pin happy = pinRepository.findByCode("p-happy").orElseThrow();
        PlaceDetailResponse placeDetail = mapPinService.getPlaceDetail(happy.getId(), null, null, null);
        assertThat(placeDetail.getName()).isEqualTo("행복식당");
        assertThat(placeDetail.getLocation()).isEqualTo("서울 서대문구 거북골로 34");
        assertThat(placeDetail.getInfoText()).isEqualTo("현금만");
        assertThat(placeDetail.getLatitude()).isEqualTo(37.5805);   // 외부 장소는 자체 좌표
        assertThat(placeDetail.getLongitude()).isEqualTo(126.9230);

        // 6) 장소 상세(내부) - 위치는 건물명 + 층수, 좌표는 소속 건물 좌표 상속
        Pin printer = pinRepository.findByCode("p-printer").orElseThrow();
        PlaceDetailResponse internalDetail = mapPinService.getPlaceDetail(printer.getId(), null, null, null);
        assertThat(internalDetail.getLocation()).isEqualTo("종합관 F1");
        assertThat(internalDetail.getLatitude()).isEqualTo(37.5803);  // 내부 장소는 건물 좌표
    }

    @Test
    @DisplayName("같은 시트를 두 번 동기화해도 중복 없이 갱신된다 (upsert)")
    void should_upsert_when_syncedTwice() throws Exception {
        // given - 한 번 동기화
        mapSyncService.syncFromSheet(objectMapper.readValue(SHEET_JSON, MapSyncDTO.SyncRequest.class));
        long afterFirst = pinRepository.count();

        // when - 동일 시트를 다시 동기화
        mapSyncService.syncFromSheet(objectMapper.readValue(SHEET_JSON, MapSyncDTO.SyncRequest.class));
        long afterSecond = pinRepository.count();

        // then - 핀 개수 그대로 (중복 생성 없이 code 기준 갱신)
        assertThat(afterSecond).isEqualTo(afterFirst);
    }
}
