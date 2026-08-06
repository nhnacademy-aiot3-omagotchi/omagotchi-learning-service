package site.omagotchi.learningservice.study.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("타이머 실행")
class TimerRunTest {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant EXPIRATION_AT = STARTED_AT.plus(Duration.ofHours(12));
    private static final TimerTimePolicy TIME_POLICY = new TimerTimePolicy(
            Duration.ofHours(12)
    );

    @Nested
    @DisplayName("시작")
    class Start {

        @Test
        @DisplayName("정상 처리")
        void startsTimerRun() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);

            assertAll(
                    () -> assertEquals(
                            COHORT_MEMBERSHIP_ID,
                            timerRun.getCohortMembershipId()
                    ),
                    () -> assertEquals(STARTED_AT, timerRun.getStartedAt()),
                    () -> assertTrue(timerRun.isRunning()),
                    () -> assertTrue(timerRun.isRunningAt(ENDED_AT, TIME_POLICY)),
                    () -> assertNull(timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertNull(timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("필수값 누락 예외")
        void rejectsMissingRequiredValues() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> TimerRun.start(null, STARTED_AT)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> TimerRun.start(COHORT_MEMBERSHIP_ID, null)
                    )
            );
        }
    }

    @Nested
    @DisplayName("현재 상태")
    class CurrentState {

        @Test
        @DisplayName("실행 중 최대 시간 정책 변경 즉시 반영")
        void appliesChangedTimePolicyToRunningTimer() {
            TimerRun timerRun = createTimerRun();
            Instant currentAt = STARTED_AT.plus(Duration.ofHours(1));
            TimerTimePolicy twoHourPolicy = new TimerTimePolicy(Duration.ofHours(2));
            TimerTimePolicy thirtyMinutePolicy = new TimerTimePolicy(Duration.ofMinutes(30));

            assertAll(
                    () -> assertTrue(timerRun.isRunningAt(currentAt, twoHourPolicy)),
                    () -> assertFalse(timerRun.isRunningAt(currentAt, thirtyMinutePolicy)),
                    () -> assertTrue(timerRun.isRunning())
            );
        }
    }

    @Nested
    @DisplayName("정상 종료")
    class Stop {

        @Test
        @DisplayName("정상 처리")
        void stopsTimerRun() {
            TimerRun timerRun = createTimerRun();
            Instant endedAt = ENDED_AT.plusMillis(999);

            TimerEndReason endReason = timerRun.stopOrExpire(endedAt, TIME_POLICY);

            assertAll(
                    () -> assertEquals(TimerEndReason.STOP, endReason),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(endedAt, timerRun.getEndedAt()),
                    () -> assertEquals(3_600L, timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.STOP, timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("0초 정상 처리")
        void stopsZeroSecondTimerRun() {
            TimerRun timerRun = createTimerRun();

            TimerEndReason endReason = timerRun.stopOrExpire(STARTED_AT, TIME_POLICY);

            assertAll(
                    () -> assertEquals(TimerEndReason.STOP, endReason),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(STARTED_AT, timerRun.getEndedAt()),
                    () -> assertEquals(0L, timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.STOP, timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("잘못된 종료값 예외와 상태 유지")
        void rejectsInvalidEndValuesAndKeepsRunning() {
            TimerRun timerRun = createTimerRun();
            Instant invalidEndedAt = STARTED_AT.minusSeconds(1);

            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> timerRun.stopOrExpire(null, TIME_POLICY)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> timerRun.stopOrExpire(invalidEndedAt, TIME_POLICY)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> timerRun.stopOrExpire(ENDED_AT, null)
                    ),
                    () -> assertTrue(timerRun.isRunning())
            );
        }

        @Test
        @DisplayName("12시간 경계 만료 우선 처리")
        void expiresAtTwelveHourBoundaryInsteadOfStopping() {
            TimerRun timerRun = createTimerRun();

            TimerEndReason endReason = timerRun.stopOrExpire(EXPIRATION_AT, TIME_POLICY);

            assertAll(
                    () -> assertEquals(TimerEndReason.EXPIRED, endReason),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(EXPIRATION_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.EXPIRED, timerRun.getEndReason())
            );
        }
    }

    @Nested
    @DisplayName("폐기")
    class Discard {

        @Test
        @DisplayName("정상 처리")
        void discardsTimerRun() {
            TimerRun timerRun = createTimerRun();

            TimerEndReason endReason = timerRun.discardOrExpire(ENDED_AT, TIME_POLICY);

            assertAll(
                    () -> assertEquals(TimerEndReason.DISCARD, endReason),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(ENDED_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.DISCARD, timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("12시간 경계 만료 우선 처리")
        void expiresAtTwelveHourBoundaryInsteadOfDiscarding() {
            TimerRun timerRun = createTimerRun();

            TimerEndReason endReason = timerRun.discardOrExpire(EXPIRATION_AT, TIME_POLICY);

            assertAll(
                    () -> assertEquals(TimerEndReason.EXPIRED, endReason),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(EXPIRATION_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.EXPIRED, timerRun.getEndReason())
            );
        }
    }

    @Nested
    @DisplayName("만료")
    class Expire {

        @Test
        @DisplayName("만료 전 실행 상태 유지")
        void keepsTimerOpenBeforeExpiration() {
            TimerRun timerRun = createTimerRun();

            boolean expired = timerRun.expireIfDue(
                    EXPIRATION_AT.minusNanos(1),
                    TIME_POLICY
            );

            assertAll(
                    () -> assertFalse(expired),
                    () -> assertTrue(timerRun.isRunning()),
                    () -> assertNull(timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertNull(timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("만료 경계 정각 정상 처리")
        void expiresTimerAtBoundary() {
            TimerRun timerRun = createTimerRun();

            boolean expired = timerRun.expireIfDue(EXPIRATION_AT, TIME_POLICY);

            assertAll(
                    () -> assertTrue(expired),
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(EXPIRATION_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.EXPIRED, timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("지연 처리 시 계산된 만료 시각 저장")
        void storesCalculatedExpirationTimeWhenCleanupIsDelayed() {
            TimerRun timerRun = createTimerRun();

            boolean expired = timerRun.expireIfDue(
                    EXPIRATION_AT.plus(Duration.ofHours(1)),
                    TIME_POLICY
            );

            assertAll(
                    () -> assertTrue(expired),
                    () -> assertEquals(EXPIRATION_AT, timerRun.getEndedAt()),
                    () -> assertEquals(TimerEndReason.EXPIRED, timerRun.getEndReason())
            );
        }
    }

    @Nested
    @DisplayName("종료 상태")
    class EndState {

        @Test
        @DisplayName("종료 후 재변경 예외")
        void rejectsTransitionAfterEnd() {
            TimerRun timerRun = createTimerRun();
            timerRun.discardOrExpire(ENDED_AT, TIME_POLICY);
            Instant invalidEndedAt = ENDED_AT.plusSeconds(1);

            assertAll(
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> timerRun.stopOrExpire(invalidEndedAt, TIME_POLICY)
                    ),
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> timerRun.discardOrExpire(invalidEndedAt, TIME_POLICY)
                    ),
                    () -> assertFalse(timerRun.expireIfDue(EXPIRATION_AT, TIME_POLICY)),
                    () -> assertEquals(ENDED_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.DISCARD, timerRun.getEndReason())
            );
        }
    }

    private TimerRun createTimerRun() {
        return TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
    }
}
