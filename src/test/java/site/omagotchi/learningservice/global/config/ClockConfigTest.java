package site.omagotchi.learningservice.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("서버 시계 설정")
class ClockConfigTest {

    @Test
    @DisplayName("현재 시각 초 단위 절삭")
    void truncatesCurrentTimeToSeconds() {
        Clock clock = new ClockConfig().utcClock();

        Instant currentAt = clock.instant();

        assertEquals(currentAt.truncatedTo(ChronoUnit.SECONDS), currentAt);
    }
}
