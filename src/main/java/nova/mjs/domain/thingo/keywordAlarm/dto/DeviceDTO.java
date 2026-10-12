package nova.mjs.domain.thingo.keywordAlarm.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * 비로그인 기기 식별자 발급 DTO.
 */
public class DeviceDTO {

    public static class Response {

        @Getter
        @RequiredArgsConstructor(staticName = "of")
        public static class Issued {
            private final UUID deviceId;
        }
    }
}
