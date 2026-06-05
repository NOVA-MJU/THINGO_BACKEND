package nova.mjs.domain.thingo.map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nova.mjs.domain.thingo.map.dto.BusArrivalResponse;
import nova.mjs.domain.thingo.map.dto.SeoulBusArrivalApiDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서울 버스 도착 API 실제 응답(JSON)을 역직렬화 → BusItem 변환까지의 데이터 흐름 검증.
 * - 네트워크 호출 없이 ws.bus.go.kr 실제 응답을 그대로 사용 (필드 매핑 검증이 목적)
 */
class BusArrivalServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusArrivalServiceImpl service = new BusArrivalServiceImpl(null, null, null, null);

    /** ws.bus.go.kr getStationByUid (arsId=13195) 실제 응답에서 발췌한 3개 노선 */
    private static final String REAL_RESPONSE = """
            {
              "msgHeader": {"headerMsg":"정상적으로 처리되었습니다.","headerCd":"0","itemCount":0},
              "msgBody": {
                "itemList": [
                  {
                    "rtNm":"7612","adirection":"영등포구청역","lastTm":"2340  ",
                    "arrmsg1":"4분후[3번째 전]","traTime1":"283","isArrive1":"0","congestion1":"3",
                    "arrmsg2":"출발대기","traTime2":"0","isArrive2":"0","congestion2":"0"
                  },
                  {
                    "rtNm":"7734","adirection":"홍대입구역","lastTm":"2300  ",
                    "arrmsg1":"곧 도착","traTime1":"5","isArrive1":"0","congestion1":"3",
                    "arrmsg2":"6분후[4번째 전]","traTime2":"415","isArrive2":"0","congestion2":"4"
                  },
                  {
                    "rtNm":"8773출근","adirection":"홍대입구역","lastTm":"0620  ",
                    "arrmsg1":"출발대기","traTime1":"0","isArrive1":"0","congestion1":"0",
                    "arrmsg2":"출발대기","traTime2":"0","isArrive2":"0","congestion2":"0"
                  }
                ]
              }
            }
            """;

    @Test
    @DisplayName("실제 API 응답이 화면 표기값(막차/남은시간/정류장수/혼잡도)으로 정확히 변환된다")
    void should_매핑되어야_한다_when_실제_API_응답_역직렬화시() throws Exception {
        // given - 실제 API JSON 역직렬화
        SeoulBusArrivalApiDto dto = objectMapper.readValue(REAL_RESPONSE, SeoulBusArrivalApiDto.class);
        List<SeoulBusArrivalApiDto.Item> items = dto.getMsgBody().getItemList();

        // when - 각 노선 항목을 BusItem으로 변환 (즐겨찾기 없음)
        Set<String> noFavorites = Set.of();
        BusArrivalResponse.BusItem bus7612 = service.toDto(items.get(0), noFavorites);
        BusArrivalResponse.BusItem bus7734 = service.toDto(items.get(1), noFavorites);
        BusArrivalResponse.BusItem bus8773 = service.toDto(items.get(2), noFavorites);

        // then - 7612: 막차 23:40, 도착 1건(둘째는 출발대기로 제외)
        assertThat(bus7612.getRouteName()).isEqualTo("7612");
        assertThat(bus7612.getLastBusTime()).isEqualTo("23:40");
        assertThat(bus7612.getArrivals()).hasSize(1);
        assertThat(bus7612.getArrivals().get(0).getRemainingTime()).isEqualTo("4분 43초");
        assertThat(bus7612.getArrivals().get(0).getStationCount()).isEqualTo("3정류장");
        assertThat(bus7612.getArrivals().get(0).getCongestion()).isEqualTo("여유");

        // then - 7734: 곧 도착 + 6분55초/4정류장/보통
        assertThat(bus7734.getLastBusTime()).isEqualTo("23:00");
        assertThat(bus7734.getArrivals()).hasSize(2);
        assertThat(bus7734.getArrivals().get(0).getRemainingTime()).isEqualTo("곧 도착");
        assertThat(bus7734.getArrivals().get(0).getCongestion()).isEqualTo("여유");
        assertThat(bus7734.getArrivals().get(1).getRemainingTime()).isEqualTo("6분 55초");
        assertThat(bus7734.getArrivals().get(1).getStationCount()).isEqualTo("4정류장");
        assertThat(bus7734.getArrivals().get(1).getCongestion()).isEqualTo("보통");

        // then - 8773출근: 막차 06:20, 도착 예정 없음(출발대기) → arrivals 비어있음
        assertThat(bus8773.getRouteName()).isEqualTo("8773출근");
        assertThat(bus8773.getLastBusTime()).isEqualTo("06:20");
        assertThat(bus8773.getArrivals()).isEmpty();

        // then - 즐겨찾기 미지정 시 favorite=false
        assertThat(bus7612.isFavorite()).isFalse();
    }

    @Test
    @DisplayName("즐겨찾기 노선 번호가 favoriteRoutes에 포함되면 favorite=true로 마킹된다")
    void should_즐겨찾기로_표시되어야_when_favoriteRoutes에_노선_포함시() throws Exception {
        // given
        SeoulBusArrivalApiDto dto = objectMapper.readValue(REAL_RESPONSE, SeoulBusArrivalApiDto.class);
        SeoulBusArrivalApiDto.Item item7612 = dto.getMsgBody().getItemList().get(0);

        // when - 7612를 즐겨찾기로 지정
        BusArrivalResponse.BusItem favorited = service.toDto(item7612, Set.of("7612"));
        BusArrivalResponse.BusItem notFavorited = service.toDto(item7612, Set.of("9999"));

        // then
        assertThat(favorited.isFavorite()).isTrue();
        assertThat(notFavorited.isFavorite()).isFalse();
    }
}
