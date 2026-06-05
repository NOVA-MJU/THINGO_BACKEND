package nova.mjs.domain.thingo.map.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 서울시 공공데이터 버스 도착 정보 API 원본 응답 매핑 DTO
 *
 * [실제 API 응답 구조 - ws.bus.go.kr getStationByUid]
 * {
 *   "comMsgHeader": { ... },        // 공통 헤더 (무시)
 *   "msgHeader": {
 *     "headerCd": "0",              // "0": 정상, "4": 결과없음, "7": 키인증실패
 *     "headerMsg": "정상...",
 *     "itemCount": 3
 *   },
 *   "msgBody": {
 *     "itemList": [ { ... }, ... ]
 *   }
 * }
 *
 * - 최상위에 ServiceResult 래퍼 없이 msgHeader, msgBody 직접 노출
 * - @JsonIgnoreProperties: comMsgHeader 등 불필요한 필드 무시
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeoulBusArrivalApiDto {

    /** 응답 헤더 (응답 코드, 메시지, 항목 수 포함) */
    private MsgHeader msgHeader;

    /** 응답 본문 (버스 도착 목록 포함) */
    private MsgBody msgBody;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MsgHeader {

        /**
         * 응답 코드
         * - "0": 정상
         * - "4": 결과 없음
         * - "7": 서비스키 인증 실패
         * - 그 외: 오류
         */
        private String headerCd;

        /** 응답 메시지 (예: "정상적으로 처리되었습니다.") */
        private String headerMsg;

        /** 조회된 버스 항목 수 (실제 API는 0으로 내려오는 경우가 있어 신뢰하지 않음) */
        private int itemCount;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MsgBody {

        /**
         * 버스 도착 정보 목록
         * - 해당 정류장을 지나는 버스 수만큼 항목 존재
         * - 도착 예정 버스가 없으면 null 또는 빈 리스트
         */
        private List<Item> itemList;
    }

    /**
     * 버스 도착 정보 항목 (API 응답 itemList 개별 항목)
     * - 필드명은 실제 ws.bus.go.kr 응답 키와 1:1 대응 (camelCase)
     */
    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /** 버스 노선 번호 (예: "7611", "7734") */
        private String rtNm;

        /** 운행 방면 / 종점 (예: "여의도", "홍대입구역") */
        private String adirection;

        /**
         * 막차 시간 (HHMM 형식, 우측 공백 패딩 존재 - 예: "2240  ")
         * - 화면의 "막차 23:30" 표기에 사용
         */
        private String lastTm;

        /**
         * 첫 번째 도착 예정 메시지 (API 원본값)
         * - 예: "5분후[3번째 전]", "곧 도착", "출발대기", "운행종료"
         * - 정류장 수("[n번째 전]") 파싱에 사용
         */
        private String arrmsg1;

        /** 두 번째 도착 예정 메시지 (API 원본값) */
        private String arrmsg2;

        /** 첫 번째 버스 도착까지 남은 시간(초) - 예: "334" */
        private String traTime1;

        /** 두 번째 버스 도착까지 남은 시간(초) */
        private String traTime2;

        /** 첫 번째 버스 현재 도착 여부 ("1": 도착) */
        private String isArrive1;

        /** 두 번째 버스 현재 도착 여부 */
        private String isArrive2;

        /**
         * 첫 번째 버스 혼잡도 코드
         * - "0": 정보없음, "3": 여유, "4": 보통, "5": 혼잡, "6": 매우혼잡
         */
        private String congestion1;

        /** 두 번째 버스 혼잡도 코드 */
        private String congestion2;
    }
}
