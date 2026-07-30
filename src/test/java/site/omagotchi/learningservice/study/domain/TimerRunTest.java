package site.omagotchi.learningservice.study.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("타이머 실행")
class TimerRunTest {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2000-01-01T01:00:00Z");

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
                            NullPointerException.class,
                            () -> TimerRun.start(null, STARTED_AT)
                    ),
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> TimerRun.start(COHORT_MEMBERSHIP_ID, null)
                    )
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

            timerRun.stop(endedAt);

            assertAll(
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

            timerRun.stop(STARTED_AT);

            assertAll(
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
                            NullPointerException.class,
                            () -> timerRun.stop(null)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> timerRun.stop(invalidEndedAt)
                    ),
                    () -> assertTrue(timerRun.isRunning())
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

            timerRun.discard(ENDED_AT);

            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(ENDED_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.DISCARD, timerRun.getEndReason())
            );
        }
    }

    @Nested
    @DisplayName("만료")
    class Expire {

        @Test
        @DisplayName("정상 처리")
        void expiresTimerRun() {
            TimerRun timerRun = createTimerRun();

            timerRun.expire(ENDED_AT);

            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(ENDED_AT, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
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
            timerRun.discard(ENDED_AT);
            Instant invalidEndedAt = ENDED_AT.plusSeconds(1);

            assertAll(
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> timerRun.stop(invalidEndedAt)
                    ),
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> timerRun.discard(invalidEndedAt)
                    ),
                    () -> assertThrows(
                            IllegalStateException.class,
                            () -> timerRun.expire(invalidEndedAt)
                    ),
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
