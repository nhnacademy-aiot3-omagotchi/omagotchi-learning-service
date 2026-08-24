package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "prediction.client")
public record PredictionClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public PredictionClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("prediction.client.base-url은 필수입니다.");
        }
        requirePositive(connectTimeout, "prediction.client.connect-timeout");
        requirePositive(readTimeout, "prediction.client.read-timeout");
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + "은 양수여야 합니다.");
        }
    }

}
