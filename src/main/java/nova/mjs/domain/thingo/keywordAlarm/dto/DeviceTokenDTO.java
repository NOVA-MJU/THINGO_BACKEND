package nova.mjs.domain.thingo.keywordAlarm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.keywordAlarm.entity.DevicePlatform;

/**
 * FCM 기기 토큰 등록/삭제 DTO.
 */
public class DeviceTokenDTO {

    public static class Request {

        @Getter
        @NoArgsConstructor
        public static class Register {

            @NotBlank(message = "FCM 토큰이 필요합니다.")
            private String fcmToken;

            @NotNull(message = "기기 플랫폼이 필요합니다.")
            private DevicePlatform platform;
        }
    }
}
