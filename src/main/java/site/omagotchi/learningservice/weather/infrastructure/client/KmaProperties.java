package site.omagotchi.learningservice.weather.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * @param baseUrl KMA 단기예보 조회서비스 base URL
 * @param serviceKey 공공데이터포털에서 발급받은 서비스키
 * @param requestTimeout 응답 대기 상한 (기본 3초)
 */
@ConfigurationProperties(prefix = "kma")
public record KmaProperties(
        String baseUrl,
        String serviceKey,
        Duration requestTimeout
) {
    public KmaProperties {
        if(Objects.isNull(baseUrl) || baseUrl.isBlank()) {
            throw new IllegalArgumentException("kma.base-url 설정이 비어 있습니다.");
        }

        if(Objects.isNull(serviceKey) || serviceKey.isBlank()) {
            throw new IllegalArgumentException("kma.service-key 설정이 비어 있습니다. KMA_SERVICE_KEY 환경변수를 확인하세요.");
        }

        if (Objects.isNull(requestTimeout) || requestTimeout.isNegative() || requestTimeout.isZero()) {
            requestTimeout = Duration.ofSeconds(3); // 기본값 3초 (3초로 한 이유는 딱히 없음)
        }
    }
}
