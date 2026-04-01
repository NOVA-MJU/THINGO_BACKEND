package nova.mjs.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.username:default}")
    private String username;

    @Value("${spring.data.redis.password}")
    private String password;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    // keyword 전용 Redis 연결 팩토리
    // application.yml 의 host, port, username, password 를 그대로 사용하고
    // database 만 2번으로 분리한다.
    @Bean(name = "keywordRedisConnectionFactory")
    public RedisConnectionFactory keywordRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setUsername(username);
        config.setPassword(RedisPassword.of(password));
        config.setDatabase(2);

        return new LettuceConnectionFactory(config);
    }

    // keyword 전용 RedisTemplate
    @Bean(name = "keywordRedisTemplate")
    public RedisTemplate<String, String> keywordRedisTemplate(
            @Qualifier("keywordRedisConnectionFactory") RedisConnectionFactory factory
    ) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}