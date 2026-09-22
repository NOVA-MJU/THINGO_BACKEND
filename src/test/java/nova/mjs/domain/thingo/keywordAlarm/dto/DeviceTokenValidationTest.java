package nova.mjs.domain.thingo.keywordAlarm.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FCM 토큰 등록 형식 검증.
 *
 * iOS 구버전이 APNs 디바이스 토큰(64자 hex)을 등록해 발송 시점에 INVALID_ARGUMENT 로 실패했다.
 * 등록 시점에 막히는지 확인한다.
 */
class DeviceTokenValidationTest {

    /** 운영 DB 의 정상 토큰과 같은 형태: "<앱인스턴스ID>:<본문>" */
    private static final String VALID_FCM_TOKEN =
            "cwomsOxdS9CNvHkzIqRkTn:APA91bF-x1Yq_3Zr8mQnL0pVdWtKhJ2sBcRfGyUiOpAsDfGhJkLzXcVbNmQwErTyUiOpAsDfGhJkLzXcVbNmQwErTyUiOpAsDfGhJkLzTTrYVk";

    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private DeviceTokenDTO.Request.Register request(String fcmToken) throws Exception {
        return objectMapper.readValue(
                "{\"fcmToken\":" + objectMapper.writeValueAsString(fcmToken) + ",\"platform\":\"IOS\"}",
                DeviceTokenDTO.Request.Register.class);
    }

    @Test
    @DisplayName("정상 FCM 등록 토큰은 통과한다")
    void valid_fcm_token_passes() throws Exception {
        Set<ConstraintViolation<DeviceTokenDTO.Request.Register>> violations =
                validator.validate(request(VALID_FCM_TOKEN));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ca664e2e61a0b1c2d3e4f5061728394a5b6c7d8e9f0a1b2c3d4e5f60715bf018", // APNs 디바이스 토큰(64자 hex)
            "ca664e2e61a0b1c2",                                                // 콜론 없는 짧은 문자열
            "abc:def:ghi",                                                     // 콜론 2개
            "abc def:ghi",                                                     // 공백 포함
            " "                                                                // 공백만(@NotBlank)
    })
    @DisplayName("FCM 형식이 아니면 등록 단계에서 거부한다")
    void invalid_token_rejected(String token) throws Exception {
        Set<ConstraintViolation<DeviceTokenDTO.Request.Register>> violations =
                validator.validate(request(token));
        assertThat(violations).isNotEmpty();
    }
}
