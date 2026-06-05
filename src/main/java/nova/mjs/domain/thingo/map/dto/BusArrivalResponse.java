package nova.mjs.domain.thingo.map.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 버스 도착 정보 API 응답 DTO
 *
 * [구조] - 앱 화면(정류장 핀 클릭 시 하단 목록)에 1:1 대응
 * BusArrivalResponse
 *  ├── arsId        : 정류장 고유 ID (서울 버스 정보 시스템 기준)
 *  ├── stationName  : 정류장 이름 (방면 포함)
 *  ├── lat / lng    : 정류장 위경도 (네이버 지도 핀 표시에 사용)
 *  └── buses        : 해당 정류장에 정차하는 버스 목록 (노선별 1개)
 *       ├── routeName    : 버스 노선 번호 (예: 7611)
 *       ├── direction    : 종점/방면 (예: 여의도)
 *       ├── lastBusTime  : 막차 시간 "HH:mm" (예: "23:30")
 *       └── arrivals     : 도착 예정 버스 목록 (0~2개, 비어있으면 "도착 예정 정보 없음")
 *            ├── remainingTime : 도착까지 남은 시간 "m분 s초" 또는 "곧 도착"
 *            ├── stationCount  : 남은 정류장 수 (예: "2정류장")
 *            └── congestion    : 혼잡도 (여유/보통/혼잡/매우혼잡)
 */
@Getter
@Builder
public class BusArrivalResponse {

    /** 정류장 고유 식별 번호 (ARS ID) */
    private String arsId;

    /** 정류장 이름 (방면 정보 포함) */
    private String stationName;

    /** 정류장 위도 (네이버 지도 마커 표시용) */
    private double lat;

    /** 정류장 경도 (네이버 지도 마커 표시용) */
    private double lng;

    /** 해당 정류장을 지나는 버스 목록 (노선별) */
    private List<BusItem> buses;

    /**
     * 개별 버스(노선) 도착 정보 DTO
     * - 공공데이터포털 getStationByUid API 응답의 itemList 항목 하나에 대응
     */
    @Getter
    @Builder
    public static class BusItem {

        /** 버스 노선 번호 (예: 7611, 7734) */
        private String routeName;

        /** 운행 방면 / 종점 (예: 여의도, 홍대입구역) */
        private String direction;

        /** 막차 시간 "HH:mm" (예: "23:30"), 정보 없으면 null */
        private String lastBusTime;

        /**
         * 현재 로그인 사용자의 즐겨찾기 여부
         * - 비로그인 시 항상 false
         * - true인 노선은 목록 상단으로 정렬됨 (화면의 채워진 별)
         */
        private boolean favorite;

        /**
         * 도착 예정 버스 목록 (최대 2개)
         * - 출발대기 / 운행종료 등 도착 예측이 없는 항목은 제외
         * - 비어있으면 프론트는 "도착 예정 정보 없음" 표기
         */
        private List<Arrival> arrivals;
    }

    /**
     * 도착 예정 버스 1대의 정보
     */
    @Getter
    @Builder
    public static class Arrival {

        /**
         * 도착까지 남은 시간
         * - traTime(초)을 "m분 s초"로 변환 (예: "5분 10초")
         * - 곧 도착하는 경우 "곧 도착"
         */
        private String remainingTime;

        /**
         * 남은 정류장 수
         * - arrmsg "[n번째 전]"에서 파싱 (예: "2정류장"), 정보 없으면 null
         */
        private String stationCount;

        /**
         * 혼잡도
         * - API 혼잡도 코드를 한글로 변환 (3→여유, 4→보통, 5→혼잡, 6→매우혼잡)
         * - 정보 없으면(코드 0) null
         */
        private String congestion;
    }
}
