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
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.event.StudyCompletedEvent;
import site.omagotchi.learningservice.study.application.port.StudyEventPublisher;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
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
import java.time.ZoneOffset;
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
            "00000000-0000-0000-0000-000000000003"
    );
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
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
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @Mock
    private StudyWriteLock studyWriteLock;

    @Mock
    private StudyEventPublisher studyEventPublisher;

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
                TIME_POLICY,
                studyEventPublisher,
                new TimerStudyRecordFactory(),
                new StudyRecordOverlapGuard(studyRecordQueryRepository)
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
                    USER_ID,
                    COHORT_ID
            );

            // advisory lock에 의한 순서 보장 검증 (잠금 -> 조회 -> 저장)
            InOrder inOrder = inOrder(studyWriteLock, timerRunQueryRepository, timerRunRepository);
            inOrder.verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
            inOrder.verify(timerRunQueryRepository).findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID);

            ArgumentCaptor<TimerRun> captor = ArgumentCaptor.forClass(TimerRun.class);
            inOrder.verify(timerRunRepository).create(captor.capture());
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
                    () -> assertEquals(0, created.getStartedAt().getNano()),
                    () -> assertTrue(created.isRunning())
            );
        }

        @Test
        @DisplayName("소수 초 시작 시각 절삭")
        void truncatesFractionalStartTimeToSeconds() {
            Instant currentAt = STARTED_AT.plusMillis(999);
            TimerCommandService service = serviceWithClock(Clock.tick(
                    Clock.fixed(currentAt, ZoneOffset.UTC),
                    Duration.ofSeconds(1)
            ));
            givenActiveMembership();
            given(timerRunQueryRepository.findActiveByCohortMembershipId(
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());
            given(timerRunRepository.create(any(TimerRun.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            TimerStateResult result = service.start(
                    USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(STARTED_AT, result.startedAt()),
                    () -> assertEquals(0, result.startedAt().getNano())
            );
        }

        @Test
        @DisplayName("활성 소속 없음 예외")
        void rejectsStartWithoutActiveMembership() {
            given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                    .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> timerCommandService.start(USER_ID, COHORT_ID)
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
                    () -> timerCommandService.start(USER_ID, COHORT_ID)
            );

            assertAll(
                    () -> assertSame(TimerErrorCode.ALREADY_RUNNING, exception.getErrorCode()),
                    () -> assertTrue(timerRun.isRunning())
            );
            verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
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
                    USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(TimerEndReason.EXPIRED, previous.getEndReason()),
                    () -> assertEquals(EXPIRATION_AT, previous.getEndedAt()),
                    () -> assertEquals(TIMER_RUN_ID, result.timerRunId()),
                    () -> assertEquals(currentAt, result.startedAt())
            );
            // advisory lock에 의한 순서 보장 검증 (잠금 -> 조회 -> 저장)
            InOrder order = inOrder(studyWriteLock, timerRunQueryRepository, timerRunRepository);
            order.verify(studyWriteLock).acquire(COHORT_MEMBERSHIP_ID);
            order.verify(timerRunQueryRepository).findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID);
            order.verify(timerRunRepository).end(previous);
            order.verify(timerRunRepository).create(any(TimerRun.class));
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
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository).save(captor.capture());
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
            order.verify(studyRecordRepository).save(saved);
            order.verify(timerRunRepository).end(timerRun);
            verify(studyEventPublisher).publishCompleted(new StudyCompletedEvent(
                    USER_ID,
                    TIMER_RUN_ID,
                    endedAt
            ));
        }

        @Test
        @DisplayName("소수 초 종료 시각 절삭")
        void truncatesFractionalEndTimeToSeconds() {
            // given
            Instant startedAt = Instant.parse("2000-01-01T18:59:00Z");
            Instant boundary = Instant.parse("2000-01-01T19:00:00Z");
            Instant endedAt = Instant.parse("2000-01-01T19:00:00.500Z");
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt);
            TimerCommandService service = serviceWithClock(Clock.tick(
                    Clock.fixed(endedAt, ZoneOffset.UTC),
                    Duration.ofSeconds(1)
            ));
            givenOwnedTimer(timerRun);

            // when
            service.stop(
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            // then
            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository).save(captor.capture());
            StudyRecord saved = captor.getValue();

            assertAll(
                    () -> assertEquals(boundary, timerRun.getEndedAt()),
                    () -> assertEquals(LocalDate.parse("2000-01-01"), saved.getAggregationDate()),
                    () -> assertEquals(startedAt, saved.getStartTime()),
                    () -> assertEquals(boundary, saved.getEndTime()),
                    () -> assertEquals(60L, saved.getStudySeconds())
            );
        }

        @Test
        @DisplayName("공부 기록을 타이머 실제 초 구간으로 저장")
        void savesStudyRecordWithExactTimerTimes() {
            Instant startedAt = STARTED_AT.plusSeconds(20);
            Instant endedAt = STARTED_AT.plusSeconds(3_640);
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);

            timerCommandService.stop(
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository).save(captor.capture());
            StudyRecord saved = captor.getValue();
            assertAll(
                    () -> assertEquals(startedAt, saved.getStartTime()),
                    () -> assertEquals(endedAt, saved.getEndTime()),
                    () -> assertEquals(3_620L, saved.getStudySeconds())
            );
        }

        @Test
        @DisplayName("초 단위로 연속 실행한 타이머의 공부 기록 구간은 겹치지 않음")
        void doesNotOverlapRecordsForConsecutiveTimers() {
            Instant firstStartedAt = Instant.parse("2000-01-01T00:00:50Z");
            Instant firstEndedAt = Instant.parse("2000-01-01T00:02:10Z");

            // firstEndedAt = secondStartedAt
            Instant secondStartedAt = Instant.parse("2000-01-01T00:02:10Z");
            Instant secondEndedAt = Instant.parse("2000-01-01T00:04:00Z");
            TimerRun firstTimer = TimerRun.start(COHORT_MEMBERSHIP_ID, firstStartedAt);
            TimerRun secondTimer = TimerRun.start(COHORT_MEMBERSHIP_ID, secondStartedAt);
            UUID secondTimerRunId = UUID.fromString(
                    "00000000-0000-0000-0000-000000000004"
            );
            givenActiveMembership();
            given(timerRunQueryRepository.findOwnedById(
                    any(UUID.class),
                    eq(COHORT_MEMBERSHIP_ID)
            )).willReturn(Optional.of(firstTimer), Optional.of(secondTimer));
            given(clock.instant()).willReturn(firstEndedAt, secondEndedAt);

            timerCommandService.stop(USER_ID, COHORT_ID, TIMER_RUN_ID);
            timerCommandService.stop(USER_ID, COHORT_ID, secondTimerRunId);

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository, times(2)).save(captor.capture());
            List<StudyRecord> records = captor.getAllValues();
            assertAll(
                    () -> assertEquals(
                            firstStartedAt,
                            records.get(0).getStartTime()
                    ),
                    () -> assertEquals(
                            firstEndedAt,
                            records.get(0).getEndTime()
                    ),
                    () -> assertEquals(
                            records.get(0).getEndTime(),
                            records.get(1).getStartTime()
                    ),
                    () -> assertEquals(
                            secondEndedAt,
                            records.get(1).getEndTime()
                    ),
                    () -> assertEquals(80L, records.get(0).getStudySeconds()),
                    () -> assertEquals(110L, records.get(1).getStudySeconds())
            );
        }

        @Test
        @DisplayName("1분 미만 타이머도 실제 초 구간으로 저장")
        void savesSubMinuteTimerAsStudyRecord() {
            Instant startedAt = STARTED_AT.plusSeconds(10);
            Instant endedAt = STARTED_AT.plusSeconds(50);
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);

            timerCommandService.stop(USER_ID, COHORT_ID, TIMER_RUN_ID);

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository).save(captor.capture());
            StudyRecord saved = captor.getValue();
            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(40L, timerRun.getMeasuredSeconds()),
                    () -> assertEquals(startedAt, saved.getStartTime()),
                    () -> assertEquals(endedAt, saved.getEndTime()),
                    () -> assertEquals(40L, saved.getStudySeconds())
            );
            verify(timerRunRepository).end(timerRun);
            verify(studyEventPublisher).publishCompleted(new StudyCompletedEvent(
                    USER_ID,
                    TIMER_RUN_ID,
                    endedAt
            ));
        }

        @Test
        @DisplayName("기존 공부 기록과 겹치면 기록 없이 OVERLAP으로 종료")
        void endsTimerAsOverlapWithoutStudyRecord() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            Instant endedAt = STARTED_AT.plusSeconds(3_600L);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(endedAt);
            given(studyRecordQueryRepository.existsActiveOverlap(
                    COHORT_MEMBERSHIP_ID,
                    STARTED_AT,
                    endedAt,
                    null
            )).willReturn(true);

            timerCommandService.stop(USER_ID, COHORT_ID, TIMER_RUN_ID);

            assertAll(
                    () -> assertFalse(timerRun.isRunning()),
                    () -> assertEquals(endedAt, timerRun.getEndedAt()),
                    () -> assertNull(timerRun.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.OVERLAP, timerRun.getEndReason())
            );
            verify(timerRunRepository).end(timerRun);
            verifyNoInteractions(studyRecordRepository, studyEventPublisher);
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
            order.verify(studyRecordRepository).save(first);
            order.verify(studyRecordRepository).save(second);
            order.verify(timerRunRepository).end(timerRun);
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
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            ArgumentCaptor<StudyRecord> captor = ArgumentCaptor.forClass(StudyRecord.class);
            verify(studyRecordRepository).save(captor.capture());
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
            verify(timerRunRepository).end(timerRun);
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
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertExpired(timerRun);
            verify(timerRunRepository).end(timerRun);
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
            verify(timerRunRepository).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }

        @Test
        @DisplayName("만료 경계에서는 만료 우선 처리")
        void expiresTimerAtBoundaryInsteadOfDiscarding() {
            TimerRun timerRun = TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT);
            givenOwnedTimer(timerRun);
            given(clock.instant()).willReturn(EXPIRATION_AT);

            timerCommandService.discard(
                    USER_ID,
                    COHORT_ID,
                    TIMER_RUN_ID
            );

            assertExpired(timerRun);
            verify(timerRunRepository).end(timerRun);
            verifyNoInteractions(studyRecordRepository);
        }
    }

    // ===== Private Methods =====

    private void givenActiveMembership() {
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    private TimerCommandService serviceWithClock(Clock configuredClock) {
        return new TimerCommandService(
                cohortAccessService,
                timerRunRepository,
                timerRunQueryRepository,
                studyRecordRepository,
                studyWriteLock,
                configuredClock,
                TIME_POLICY,
                studyEventPublisher,
                new TimerStudyRecordFactory(),
                new StudyRecordOverlapGuard(studyRecordQueryRepository)
        );
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
