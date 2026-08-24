package site.omagotchi.learningservice.prediction.infrastructure.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("예측 서비스 HTTP 설정")
class PredictionClientPropertiesTest {

    @Test
    @DisplayName("양수 timeout 설정 정상 처리")
    void acceptsPositiveTimeouts() {
        // When
        PredictionClientProperties properties = new PredictionClientProperties(
                "http://prediction-service",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );

        // Then
        assertEquals(Duration.ofSeconds(2), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(5), properties.readTimeout());
    }

    @Test
    @DisplayName("0 이하 timeout 설정 예외")
    void rejectsNonPositiveTimeouts() {
        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PredictionClientProperties(
                        "http://prediction-service",
                        Duration.ZERO,
                        Duration.ofSeconds(5)
                )
        );

        // Then
        assertEquals(
                "prediction.client.connect-timeout은 양수여야 합니다.",
                exception.getMessage()
        );
    }
}
