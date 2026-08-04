package site.omagotchi.learningservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.study.application.TimerCommandService;
import site.omagotchi.learningservice.study.application.port.StudyRecordRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.TimerRunJpaRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@DisplayName("타이머 정지와 공부 기록 저장")
class TimerCommandServiceIT {

    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T18:59:00Z");
    private static final Instant BOUNDARY = Instant.parse("2000-01-01T19:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2000-01-01T19:01:00Z");
    private static final Instant SINGLE_RECORD_STARTED_AT = Instant.parse(
            "2000-01-01T00:00:00Z"
    );
    private static final Instant SINGLE_RECORD_ENDED_AT = Instant.parse(
            "2000-01-01T01:00:00Z"
    );

    @Autowired
    private TimerCommandService timerCommandService;

    @Autowired
    private TimerRunRepository timerRunRepository;

    @Autowired
    private TimerRunJpaRepository timerRunJpaRepository;

    @Autowired
    private StudyRecordJpaRepository studyRecordJpaRepository;

    @MockitoBean
    private CohortAccessService cohortAccessService;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private StudyRecordRepository studyRecordRepository;

    @BeforeEach
    void setUp() {
        studyRecordJpaRepository.deleteAllInBatch();
        timerRunJpaRepository.deleteAllInBatch();
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("KST 04시 경계 분할과 타이머 종료 동시 커밋")
    void commitsSplitRecordsAndTimerEndTogether() {
        TimerRun timerRun = saveRunningTimer();
        given(clock.instant()).willReturn(ENDED_AT);

        timerCommandService.stop(
                COMMAND_ID,
                USER_ID,
                COHORT_ID,
                timerRun.getId()
        );

        TimerRun endedTimer = timerRunJpaRepository.findById(timerRun.getId()).orElseThrow();
        List<StudyRecord> records = studyRecordJpaRepository.findAll().stream()
                .sorted(Comparator.comparing(StudyRecord::getStartTime))
                .toList();
        assertAll(
                () -> assertEquals(TimerEndReason.STOP, endedTimer.getEndReason()),
                () -> assertEquals(ENDED_AT, endedTimer.getEndedAt()),
                () -> assertEquals(120L, endedTimer.getMeasuredSeconds()),
                () -> assertEquals(2, records.size()),
                () -> assertEquals(
                        LocalDate.parse("2000-01-01"),
                        records.get(0).getAggregationDate()
                ),
                () -> assertEquals(STARTED_AT, records.get(0).getStartTime()),
                () -> assertEquals(BOUNDARY, records.get(0).getEndTime()),
                () -> assertEquals(60L, records.get(0).getStudySeconds()),
                () -> assertEquals(
                        LocalDate.parse("2000-01-02"),
                        records.get(1).getAggregationDate()
                ),
                () -> assertEquals(BOUNDARY, records.get(1).getStartTime()),
                () -> assertEquals(ENDED_AT, records.get(1).getEndTime()),
                () -> assertEquals(60L, records.get(1).getStudySeconds())
        );
    }

    @Test
    @DisplayName("경계 미교차 기록 한 건과 타이머 종료 동시 커밋")
    void commitsSingleRecordAndTimerEndTogether() {
        TimerRun timerRun = saveRunningTimer(SINGLE_RECORD_STARTED_AT);
        given(clock.instant()).willReturn(SINGLE_RECORD_ENDED_AT);

        timerCommandService.stop(
                COMMAND_ID,
                USER_ID,
                COHORT_ID,
                timerRun.getId()
        );

        TimerRun endedTimer = timerRunJpaRepository.findById(timerRun.getId()).orElseThrow();
        List<StudyRecord> records = studyRecordJpaRepository.findAll();
        assertAll(
                () -> assertEquals(TimerEndReason.STOP, endedTimer.getEndReason()),
                () -> assertEquals(SINGLE_RECORD_ENDED_AT, endedTimer.getEndedAt()),
                () -> assertEquals(3_600L, endedTimer.getMeasuredSeconds()),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(
                        LocalDate.parse("2000-01-01"),
                        records.getFirst().getAggregationDate()
                ),
                () -> assertEquals(SINGLE_RECORD_STARTED_AT, records.getFirst().getStartTime()),
                () -> assertEquals(SINGLE_RECORD_ENDED_AT, records.getFirst().getEndTime()),
                () -> assertEquals(3_600L, records.getFirst().getStudySeconds())
        );
    }

    @Test
    @DisplayName("타이머 초 정밀도와 공부 기록 분 정밀도 저장")
    void persistsTimerAtSecondsAndStudyRecordAtMinutes() {
        Instant startedAt = Instant.parse("2000-01-01T00:00:20Z");
        Instant endedAt = Instant.parse("2000-01-01T00:01:40Z");
        given(clock.instant()).willReturn(startedAt, endedAt);

        TimerStateResult started = timerCommandService.start(
                COMMAND_ID,
                USER_ID,
                COHORT_ID
        );
        timerCommandService.stop(
                COMMAND_ID,
                USER_ID,
                COHORT_ID,
                started.timerRunId()
        );

        TimerRun endedTimer = timerRunJpaRepository
                .findById(started.timerRunId())
                .orElseThrow();
        StudyRecord studyRecord = studyRecordJpaRepository.findAll().getFirst();
        assertAll(
                () -> assertEquals(startedAt, endedTimer.getStartedAt()),
                () -> assertEquals(endedAt, endedTimer.getEndedAt()),
                () -> assertEquals(0, endedTimer.getStartedAt().getNano()),
                () -> assertEquals(0, endedTimer.getEndedAt().getNano()),
                () -> assertEquals(
                        Instant.parse("2000-01-01T00:00:00Z"),
                        studyRecord.getStartTime()
                ),
                () -> assertEquals(
                        Instant.parse("2000-01-01T00:02:00Z"),
                        studyRecord.getEndTime()
                ),
                () -> assertEquals(80L, studyRecord.getStudySeconds())
        );
    }

    @Test
    @DisplayName("공부 기록 저장 실패 시 타이머 정지 롤백")
    void rollsBackTimerStopWhenStudyRecordSaveFails() {
        TimerRun timerRun = saveRunningTimer();
        given(clock.instant()).willReturn(ENDED_AT);

        // 예외 발생 모킹
        doThrow(new RuntimeException("DB 저장 실패"))
                .when(studyRecordRepository).save(any(StudyRecord.class));

        // 예외 던져짐 확인
        assertThrows(RuntimeException.class, () -> timerCommandService.stop(
                COMMAND_ID,
                USER_ID,
                COHORT_ID,
                timerRun.getId()
        ));

        // 원자적 롤백 검증
        TimerRun rolledBackTimer = timerRunJpaRepository.findById(timerRun.getId()).orElseThrow();
        List<StudyRecord> records = studyRecordJpaRepository.findAll();

        assertAll(
                () -> assertTrue(rolledBackTimer.isRunning()),
                () -> assertNull(rolledBackTimer.getEndReason()),
                () -> assertNull(rolledBackTimer.getEndedAt()),
                () -> assertTrue(records.isEmpty())
        );
    }

    private TimerRun saveRunningTimer() {
        return saveRunningTimer(STARTED_AT);
    }

    private TimerRun saveRunningTimer(Instant startedAt) {
        return timerRunRepository.create(
                TimerRun.start(COHORT_MEMBERSHIP_ID, startedAt)
        );
    }
}
