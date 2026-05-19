package nova.mjs.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean(name = "openWeatherMapClient")
    public WebClient openWeatherMapClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://api.openweathermap.org")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean(name = "youtubeApiClient")
    public WebClient youtubeApiClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 서울시 공공데이터 버스 도착 정보 API 클라이언트
     * - 공공데이터포털(data.go.kr) 서울 버스 도착 정보 서비스 호출에 사용
     * - 기본 URL: ws.bus.go.kr (서울 버스 실시간 정보 시스템)
     */
    @Bean(name = "seoulBusApiClient")
    public WebClient seoulBusApiClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://ws.bus.go.kr/api/rest/stationinfo")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
