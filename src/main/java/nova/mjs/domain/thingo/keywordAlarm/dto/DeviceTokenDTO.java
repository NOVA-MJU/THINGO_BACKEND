package nova.mjs.domain.thingo.keywordAlarm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

            /**
             * FCM 등록 토큰. "<앱인스턴스ID>:<본문>" 형태로 콜론을 포함한다.
             * iOS 구버전이 APNs 디바이스 토큰(64자 hex)을 그대로 올려 발송 시점에야 실패한 적이 있어,
             * 등록 시점에 형식을 막는다.
             */
            @NotBlank(message = "FCM 토큰이 필요합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$",
                    message = "FCM 등록 토큰 형식이 아닙니다. (APNs 디바이스 토큰이 아닌 FCM 토큰을 보내주세요)")
            private String fcmToken;

            @NotNull(message = "기기 플랫폼이 필요합니다.")
            private DevicePlatform platform;
        }
    }
}
