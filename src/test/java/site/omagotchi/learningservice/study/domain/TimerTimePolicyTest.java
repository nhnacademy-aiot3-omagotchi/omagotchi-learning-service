package site.omagotchi.learningservice.study.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("타이머 시간 정책")
class TimerTimePolicyTest {

    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Duration MAX_DURATION = Duration.ofHours(12);
    private static final TimerTimePolicy POLICY = new TimerTimePolicy(MAX_DURATION);

    @Nested
    @DisplayName("최대 실행 시간")
    class MaxDuration {

        @Test
        @DisplayName("필수값 누락과 0 이하 예외")
        void rejectsMissingOrNonPositiveDuration() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new TimerTimePolicy(null)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new TimerTimePolicy(Duration.ZERO)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new TimerTimePolicy(Duration.ofSeconds(-1))
                    )
            );
        }

        @Test
        @DisplayName("설정된 최대 실행 시간 적용")
        void appliesConfiguredMaxDuration() {
            TimerTimePolicy policy = new TimerTimePolicy(Duration.ofMinutes(37));

            assertEquals(
                    Instant.parse("2000-01-01T00:37:00Z"),
                    policy.expirationAt(STARTED_AT)
            );
        }
    }

    @Nested
    @DisplayName("만료 판정")
    class Expiration {

        @Test
        @DisplayName("경계 직전 실행 상태")
        void keepsRunningBeforeBoundary() {
            Instant currentAt = STARTED_AT.plus(MAX_DURATION).minusNanos(1);

            assertFalse(POLICY.isExpired(STARTED_AT, currentAt));
        }

        @Test
        @DisplayName("경계 정각부터 만료")
        void expiresAtAndAfterBoundary() {
            Instant expirationAt = STARTED_AT.plus(MAX_DURATION);

            assertAll(
                    () -> assertTrue(POLICY.isExpired(STARTED_AT, expirationAt)),
                    () -> assertTrue(
                            POLICY.isExpired(STARTED_AT, expirationAt.plusNanos(1))
                    )
            );
        }
    }

    @Nested
    @DisplayName("경과 초")
    class ElapsedSeconds {

        @Test
        @DisplayName("내림 처리")
        void floorsElapsedSeconds() {
            Instant currentAt = STARTED_AT.plusSeconds(1).plusMillis(999);

            assertEquals(1L, POLICY.elapsedSeconds(STARTED_AT, currentAt));
        }

        @Test
        @DisplayName("시작 전 시각 0초 처리")
        void clampsNegativeElapsedSecondsToZero() {
            Instant currentAt = STARTED_AT.minusNanos(1);

            assertEquals(0L, POLICY.elapsedSeconds(STARTED_AT, currentAt));
        }
    }
}
