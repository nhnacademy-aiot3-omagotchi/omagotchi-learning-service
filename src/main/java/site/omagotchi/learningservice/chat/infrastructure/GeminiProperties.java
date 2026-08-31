package site.omagotchi.learningservice.chat.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 팀원들이 각자 발급받은 Gemini API 키 목록
 *
 * @param apiKeys GEMINI_API_KEYS 환경변수를 콤마로 잘라 담는다 (예: aaa,bbb,ccc)
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(List<String> apiKeys) {

    public GeminiProperties {
        if (Objects.isNull(apiKeys) || apiKeys.isEmpty()) {
            throw new IllegalArgumentException("gemini.api-keys 설정이 비어 있습니다. GEMINI_API_KEYS 환경변수를 확인하세요.");
        }

        for (int i = 0; i < apiKeys.size(); i++) {
            String apiKey = apiKeys.get(i);

            if (Objects.isNull(apiKey) || apiKey.isBlank()) {
                throw new IllegalArgumentException("gemini.api-keys의 " + (i + 1) + "번째 키가 비어 있습니다.");
            }
        }

        apiKeys = List.copyOf(apiKeys);
    }
}
