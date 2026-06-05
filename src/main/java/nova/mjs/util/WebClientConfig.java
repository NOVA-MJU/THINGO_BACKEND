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
     * 서울시 공공데이터 버스 도착정보 API 클라이언트.
     * BusArrivalServiceImpl 가 @Qualifier("seoulBusApiClient") 로 주입받는다.
     * baseUrl: http://ws.bus.go.kr/api/rest
     */
    @Bean(name = "seoulBusApiClient")
    public WebClient seoulBusApiClient(WebClient.Builder builder) {
        return builder
                .baseUrl("http://ws.bus.go.kr/api/rest")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
