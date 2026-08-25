package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("타이머 현재 상태 조회")
class TimerQueryServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TIMER_RUN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Duration MAX_DURATION = Duration.ofHours(12);

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private TimerRunQueryRepository timerRunQueryRepository;

    @Mock
    private Clock clock;

    private TimerQueryService timerQueryService;

    @BeforeEach
    void setUp() {
        timerQueryService = new TimerQueryService(
                cohortAccessService,
                timerRunQueryRepository,
                clock,
                new TimerTimePolicy(MAX_DURATION)
        );
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    @Nested
    @DisplayName("현재 상태")
    class GetCurrent {

        @Test
        @DisplayName("실행 중 타이머 정상 조회")
        void returnsRunningTimer() {
            Instant currentAt = STARTED_AT.plusSeconds(3_600L).plusMillis(999L);
            givenRunningTimerAt(currentAt);

            TimerStateResult result = timerQueryService.getCurrent(USER_ID, COHORT_ID);

            assertAll(
                    () -> assertEquals(TIMER_RUN_ID, result.timerRunId()),
                    () -> assertEquals(TimerStateResult.State.RUNNING, result.state()),
                    () -> assertEquals(STARTED_AT, result.startedAt()),
                    () -> assertEquals(3_600L, result.elapsedSeconds())
            );
        }

        @Test
        @DisplayName("활성 실행 없음 처리")
        void returnsStoppedWhenActiveTimerDoesNotExist() {
            given(timerRunQueryRepository.findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID))
                    .willReturn(Optional.empty());

            TimerStateResult result = timerQueryService.getCurrent(USER_ID, COHORT_ID);

            assertStopped(result);
            verifyNoInteractions(clock);
        }

        @Test
        @DisplayName("만료 경계 정지 상태 반환과 실행 무변경")
        void returnsStoppedAtTwelveHourExpirationBoundary() {
            TimerRun timerRun = givenRunningTimerAt(STARTED_AT.plus(MAX_DURATION));

            TimerStateResult result = timerQueryService.getCurrent(USER_ID, COHORT_ID);

            assertStopped(result);
            assertAll(
                    () -> assertTrue(timerRun.isRunning()),
                    () -> assertNull(timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertNull(timerRun.getEndReason())
            );
        }

        @Test
        @DisplayName("활성 소속 없음 예외")
        void rejectsQueryWithoutActiveMembership() {
            given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                    .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerQueryService.getCurrent(USER_ID, COHORT_ID)
            );

            assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
            verifyNoInteractions(timerRunQueryRepository, clock);
        }

    }

    private TimerRun givenRunningTimerAt(Instant currentAt) {
        TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
        ReflectionTestUtils.setField(timerRun, "id", TIMER_RUN_ID);
        given(timerRunQueryRepository.findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID))
                .willReturn(Optional.of(timerRun));
        given(clock.instant()).willReturn(currentAt);
        return timerRun;
    }

    private void assertStopped(TimerStateResult result) {
        assertAll(
                () -> assertNull(result.timerRunId()),
                () -> assertEquals(TimerStateResult.State.STOPPED, result.state()),
                () -> assertNull(result.startedAt()),
                () -> assertEquals(0L, result.elapsedSeconds())
        );
    }
}
