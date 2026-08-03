package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.StudyWriteLock;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("타이머 명령")
class TimerCommandServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID TIMER_RUN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant EXPIRATION_AT = Instant.parse("2000-01-01T12:00:00Z");
    private static final TimerTimePolicy TIME_POLICY = new TimerTimePolicy(
            Duration.ofHours(12)
    );

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private TimerRunRepository timerRunRepository;

    @Mock
    private TimerRunQueryRepository timerRunQueryRepository;

    @Mock
    private StudyRecordRepository studyRecordRepository;

    @Mock
    private StudyWriteLock studyWriteLock;

    @Mock
    private Clock clock;

    private TimerCommandService timerCommandService;

    @BeforeEach
    void setUp() {
        timerCommandService = new TimerCommandService(
                cohortAccessService,
                timerRunRepository,
                timerRunQueryRepository,
                studyRecordRepository,
                studyWriteLock,
                clock,
                TIME_POLICY
        );
    }

    @Nested
    @DisplayName("타이머 시작")
    class StartTimer {

        @Test
        @DisplayName("정상 처리")
        void startsTimer() {
            givenActiveMembership();
            given(timerRunQueryRepository.findActiveByCohortMembershipId(
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());
            given(clock.instant()).willReturn(STARTED_AT);
            given(timerRunRepository.create(any(TimerRun.class)))
                    .willAnswer(invocation -> {
                        TimerRun timerRun = invocation.getArgument(0);
                        ReflectionTestUtils.setField(timerRun, "id", TIMER_RUN_ID);
                        return timerRun;
                    });

            TimerStateResult result = timerCommandService.start(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID
            );

            ArgumentCaptor<TimerRun> captor = ArgumentCaptor.forClass(TimerRun.class);
            verify(timerRunRepository, times(1)).create(captor.capture());
            TimerRun created = captor.getValue();
            assertAll(
                    () -> assertEquals(TIMER_RUN_ID, result.timerRunId()),
                    () -> assertEquals(TimerStateResult.State.RUNNING, result.state()),
                    () -> assertEquals(STARTED_AT, result.startedAt()),
                    () -> assertEquals(0L, result.elapsedSeconds()),
                    () -> assertEquals(
                            COHORT_MEMBERSHIP_ID,
                            created.getCohortMembershipId()
                    ),
                    () -> assertEquals(STARTED_AT, created.getStartedAt()),
                    () -> assertTrue(created.isRunning())
            );
            verify(studyWriteLock, times(1)).acquire(COHORT_MEMBERSHIP_ID);
        }

        @Test
        @DisplayName("활성 소속 없음 예외")
        void rejectsStartWithoutActiveMembership() {
            given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                    .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerCommandService.start(COMMAND_ID, USER_ID, COHORT_ID)
            );

            assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
            verifyNoInteractions(
                    studyWriteLock,
                    timerRunQueryRepository,
                    timerRunRepository,
                    clock
            );
        }

        @Test
        @DisplayName("실행 중인 타이머 존재 예외")
        void rejectsStartWhenTimerIsAlreadyRunning() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            givenActiveMembership();
            given(timerRunQueryRepository.findActiveByCohortMembershipId(
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(timerRun));
            given(clock.instant()).willReturn(STARTED_AT.plusSeconds(3_600L));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerCommandService.start(COMMAND_ID, USER_ID, COHORT_ID)
            );

            assertAll(
                    () -> assertSame(TimerErrorCode.ALREADY_RUNNING, exception.getErrorCode()),
                    () -> assertTrue(timerRun.isRunning())
            );
            verify(studyWriteLock, times(1)).acquire(COHORT_MEMBERSHIP_ID);
            verifyNoInteractions(timerRunRepository);
        }

        @Test
        @DisplayName("만료 실행 종료 후 정상 처리")
        void startsTimerAfterExpiringPreviousRun() {
            TimerRun previous = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            Instant currentAt = EXPIRATION_AT.plusSeconds(3_600L);
            givenActiveMembership();
            given(timerRunQueryRepository.findActiveByCohortMembershipId(
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.of(previous));
            given(clock.instant()).willReturn(currentAt);
            given(timerRunRepository.create(any(TimerRun.class)))
                    .willAnswer(invocation -> {
                        TimerRun timerRun = invocation.getArgument(0);
                        ReflectionTestUtils.setField(timerRun, "id", TIMER_RUN_ID);
                        return timerRun;
                    });

            TimerStateResult result = timerCommandService.start(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(TimerEndReason.EXPIRED, previous.getEndReason()),
                    () -> assertEquals(EXPIRATION_AT, previous.getEndedAt()),
                    () -> assertEquals(TIMER_RUN_ID, result.timerRunId()),
                    () -> assertEquals(currentAt, result.startedAt())
            );
            InOrder order = inOrder(timerRunRepository);
            order.verify(timerRunRepository, times(1)).end(previous);
            order.verify(timerRunRepository, times(1)).create(any(TimerRun.class));
        }
    }

    @Nested
    @DisplayName("정상 종료")
    class Stop {

        @Test
        @DisplayName("정상 처리 (경계 미교차)")
        void savesSingleStudyRecordWithoutBoundaryCrossing() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            Instant endedAt = STARTED_AT.plusSeconds(3_600L);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);

            timerCommandService.stop(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository, times(1)).save(captor.capture());
            StudyRecord saved = captor.getValue();
            assertAll(
                    () -> assertEquals(COHORT_MEMBERSHIP_ID, saved.getCohortMembershipId()),
                    () -> assertEquals(LocalDate.parse("2000-01-01"), saved.getAggregationDate()),
                    () -> assertEquals(STARTED_AT, saved.getStartTime()),
                    () -> assertEquals(endedAt, saved.getEndTime()),
                    () -> assertEquals(3_600L, saved.getStudySeconds()),
                    () -> assertEquals(TimerEndReason.STOP, timerRun.getEndReason())
            );
            InOrder order = inOrder(studyRecordRepository, timerRunRepository);
            order.verify(studyRecordRepository, times(1)).save(saved);
            order.verify(timerRunRepository, times(1)).end(timerRun);
        }

        @Test
        @DisplayName("정상 처리 (경계 교차)")
        void splitsStudyRecordsAtKstBoundary() {
            Instant startedAt = Instant.parse("2000-01-01T18:59:00Z");
            Instant boundary = Instant.parse("2000-01-01T19:00:00Z");
            Instant endedAt = Instant.parse("2000-01-01T19:01:00Z");
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);

            timerCommandService.stop(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository, times(2)).save(captor.capture());
            List<StudyRecord> savedRecords = captor.getAllValues();
            StudyRecord first = savedRecords.get(0);
            StudyRecord second = savedRecords.get(1);
            assertAll(
                    () -> assertEquals(LocalDate.parse("2000-01-01"), first.getAggregationDate()),
                    () -> assertEquals(startedAt, first.getStartTime()),
                    () -> assertEquals(boundary, first.getEndTime()),
                    () -> assertEquals(60L, first.getStudySeconds()),
                    () -> assertEquals(LocalDate.parse("2000-01-02"), second.getAggregationDate()),
                    () -> assertEquals(boundary, second.getStartTime()),
                    () -> assertEquals(endedAt, second.getEndTime()),
                    () -> assertEquals(60L, second.getStudySeconds()),
                    () -> assertEquals(
                            timerRun.getMeasuredSeconds(),
                            first.getStudySeconds() + second.getStudySeconds()
                    )
            );
            InOrder order = inOrder(studyRecordRepository, timerRunRepository);
            order.verify(studyRecordRepository, times(1)).save(first);
            order.verify(studyRecordRepository, times(1)).save(second);
            order.verify(timerRunRepository, times(1)).end(timerRun);
        }

        @Test
        @DisplayName("정상 처리 (경계 시각 종료)")
        void savesSingleStudyRecordWhenEndTimeEqualsBoundary() {
            Instant startedAt = Instant.parse("2000-01-01T18:59:00Z");
            Instant boundary = Instant.parse("2000-01-01T19:00:00Z");
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(boundary);

            timerCommandService.stop(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository, times(1)).save(captor.capture());
            StudyRecord saved = captor.getValue();
            assertAll(
                    () -> assertEquals(LocalDate.parse("2000-01-01"), saved.getAggregationDate()),
                    () -> assertEquals(startedAt, saved.getStartTime()),
                    () -> assertEquals(boundary, saved.getEndTime()),
                    () -> assertEquals(60L, saved.getStudySeconds())
            );
        }

        @Test
        @DisplayName("0초 종료 시, 기록을 저장하지 않음")
        void stopsZeroSecondTimerWithoutRecord() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(STARTED_AT);

            timerCommandService.stop(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(STARTED_AT, timerRun.getEndedAt()),
                    () -> assertEquals(0L, timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.STOP, timerRun.getEndReason())
            );
            verify(timerRunRepository, times(1)).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }

        @Test
        @DisplayName("대상 없음 예외")
        void rejectsStopWhenOwnedTimerDoesNotExist() {
            givenActiveMembership();
            given(timerRunQueryRepository.findOwnedById(
                    TIMER_RUN_ID,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerCommandService.stop(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            TIMER_RUN_ID
                    )
            );

            assertSame(TimerErrorCode.RUN_NOT_FOUND, exception.getErrorCode());
            verifyNoInteractions(clock, timerRunRepository, studyRecordRepository);
        }

        @Test
        @DisplayName("종료 실행 예외")
        void rejectsStopWhenTimerAlreadyEnded() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            timerRun.discardOrExpire(STARTED_AT.plusSeconds(60L), TIME_POLICY);
            givenOwnedTimer(timerRun);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerCommandService.stop(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            TIMER_RUN_ID
                    )
            );

            assertSame(TimerErrorCode.ALREADY_ENDED, exception.getErrorCode());
            verifyNoInteractions(clock, timerRunRepository, studyRecordRepository);
        }

        @Test
        @DisplayName("만료 경계에서는 만료 우선 처리")
        void expiresTimerAtBoundaryInsteadOfStopping() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(EXPIRATION_AT);

            timerCommandService.stop(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertExpired(timerRun);
            verify(timerRunRepository, times(1)).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }

        @Test
        @DisplayName("공부 기록 저장 실패 시 타이머 종료 저장 안 함")
        void doesNotPersistTimerEndWhenStudyRecordSaveFails() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            Instant endedAt = STARTED_AT.plusSeconds(3_600L);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);
            given(studyRecordRepository.save(any(StudyRecord.class)))
                    .willThrow(new RuntimeException("저장 실패"));

            assertThrows(
                    RuntimeException.class,
                    () -> timerCommandService.stop(
                            COMMAND_ID,
                            USER_ID,
                            COHORT_ID,
                            TIMER_RUN_ID
                    )
            );

            verify(timerRunRepository, never()).end(timerRun);
        }
    }

    @Nested
    @DisplayName("폐기")
    class Discard {

        @Test
        @DisplayName("정상 처리")
        void discardsRunningTimer() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            Instant currentAt = STARTED_AT.plusSeconds(3_600L);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(currentAt);

            timerCommandService.discard(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(currentAt, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.DISCARD, timerRun.getEndReason())
            );
            verify(timerRunRepository, times(1)).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }

        @Test
        @DisplayName("만료 경계에서는 만료 우선 처리")
        void expiresTimerAtBoundaryInsteadOfDiscarding() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(EXPIRATION_AT);

            timerCommandService.discard(
                    COMMAND_ID,
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertExpired(timerRun);
            verify(timerRunRepository, times(1)).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }
    }

    // ===== Private Methods =====

    private void givenActiveMembership() {
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    private void givenOwnedTimer(TimerRun timerRun) {
        givenActiveMembership();
        given(timerRunQueryRepository.findOwnedById(
                TIMER_RUN_ID,
                COHORT_MEMBERSHIP_ID
        )).willReturn(Optional.of(timerRun));
    }


    private void assertExpired(TimerRun timerRun) {
        assertAll(
                () -> assertFalse(timerRun.isRunning()),
                () -> assertEquals(EXPIRATION_AT, timerRun.getEndedAt()),
                () -> assertNull(timerRun.getMeasuredSeconds()),
                () -> assertEquals(TimerEndReason.EXPIRED, timerRun.getEndReason())
        );
    }
}
