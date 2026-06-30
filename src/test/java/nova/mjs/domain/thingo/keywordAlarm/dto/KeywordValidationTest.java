package nova.mjs.domain.thingo.keywordAlarm.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 키워드 입력 검증(공백 제외 5글자 이내) - 화면 06-2-3 조건(5번).
 */
class KeywordValidationTest {

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

    private KeywordSubscriptionDTO.Request.Create request(String keyword) throws Exception {
        // 키워드만 바꿔 역직렬화 (categories 는 별도 검증)
        return objectMapper.readValue(
                "{\"keyword\":" + objectMapper.writeValueAsString(keyword) + ",\"categories\":[\"NOTICE\"]}",
                KeywordSubscriptionDTO.Request.Create.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"수강신청", "장학", "a", "중간고사"})
    @DisplayName("공백 없는 1~5글자 키워드는 통과한다")
    void should_pass_when_유효한키워드(String keyword) throws Exception {
        var violations = validator.validate(request(keyword));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"수강 신청", "키 워드", " 장학", "장학 "})
    @DisplayName("공백이 포함되면 실패하고 안내 문구를 노출한다")
    void should_fail_when_공백포함(String keyword) throws Exception {
        var violations = validator.validate(request(keyword));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().equals("올바른 형식의 키워드를 입력해 주세요."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"여섯글자키워드", "123456", "수강신청정정"})
    @DisplayName("6글자 이상이면 실패한다")
    void should_fail_when_5글자초과(String keyword) throws Exception {
        var violations = validator.validate(request(keyword));
        assertThat(violations).isNotEmpty();
    }

    @DisplayName("카테고리가 비어 있으면 실패한다")
    @org.junit.jupiter.api.Test
    void should_fail_when_카테고리_없음() throws Exception {
        KeywordSubscriptionDTO.Request.Create req = objectMapper.readValue(
                "{\"keyword\":\"장학\",\"categories\":[]}",
                KeywordSubscriptionDTO.Request.Create.class);

        Set<ConstraintViolation<KeywordSubscriptionDTO.Request.Create>> violations = validator.validate(req);

        assertThat(violations).anyMatch(v -> v.getMessage().equals("알림 카테고리를 1개 이상 선택해 주세요."));
    }
}
