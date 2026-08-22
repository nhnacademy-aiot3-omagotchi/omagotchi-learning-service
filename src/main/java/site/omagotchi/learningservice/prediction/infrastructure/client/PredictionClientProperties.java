package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prediction.client")
public record PredictionClientProperties(
        String baseUrl
) {
}
