package nova.mjs.util.profanity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 비속어 사전 로더.
 *
 * - classpath 의 profanity/badwords.txt 를 애플리케이션 기동 시 1회 읽어 메모리에 올린다.
 * - '#' 로 시작하는 주석 줄과 빈 줄은 무시한다.
 * - 사전 파일이 없거나 비어 있어도 기동은 막지 않는다(빈 사전 = 필터 미동작).
 */
@Slf4j
@Component
public class ProfanityDictionary {

    private static final String RESOURCE_PATH = "profanity/badwords.txt";

    private final List<String> words;

    public ProfanityDictionary() {
        this.words = load();
        log.info("비속어 사전 로딩 완료 - 단어 수: {}", words.size());
    }

    /** 로딩된 비속어 목록(불변). */
    public List<String> words() {
        return words;
    }

    /**
     * 사전 파일을 읽어 유효한 단어만 추린다.
     * 파일이 없으면 빈 목록을 반환한다.
     */
    private List<String> load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("비속어 사전 파일을 찾지 못했습니다 - path: {}", RESOURCE_PATH);
            return Collections.emptyList();
        }

        List<String> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                // 주석/빈 줄은 건너뛴다
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                loaded.add(word);
            }
        } catch (IOException e) {
            log.error("비속어 사전 로딩 실패 - path: {}", RESOURCE_PATH, e);
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(loaded);
    }
}
