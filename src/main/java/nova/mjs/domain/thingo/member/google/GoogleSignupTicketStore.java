package nova.mjs.domain.thingo.member.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import nova.mjs.util.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 처음 온 학교 계정이 추가 정보를 입력하는 동안 검증된 구글 계정 정보를 맡아 둔다.
 * 가입 요청은 이 표(signupToken)로만 계정 정보를 꺼내므로, 클라이언트가 이메일을 바꿔 보낼 수 없다
 */
@Component
@RequiredArgsConstructor
public class GoogleSignupTicketStore {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "google:signup:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public String issue(GoogleAccount account) {
        String ticket = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(KEY_PREFIX + ticket, objectMapper.writeValueAsString(account), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("구글 가입 정보 직렬화 실패", e);
        }
        return ticket;
    }

    public GoogleAccount get(String ticket) {
        String json = redis.opsForValue().get(KEY_PREFIX + ticket);
        if (json == null) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_SIGNUP_EXPIRED);
        }
        try {
            return objectMapper.readValue(json, GoogleAccount.class);
        } catch (JsonProcessingException e) {
            throw new GoogleAuthException(ErrorCode.GOOGLE_SIGNUP_EXPIRED, e);
        }
    }

    // 가입이 끝난 뒤에 지운다. 닉네임 중복처럼 입력을 고쳐 다시 보내야 할 때는 같은 표를 계속 쓸 수 있어야 한다
    public void remove(String ticket) {
        redis.delete(KEY_PREFIX + ticket);
    }
}
