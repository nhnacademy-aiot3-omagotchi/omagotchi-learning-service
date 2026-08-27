package site.omagotchi.learningservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.study.application.port.TimerRunQueryRepository;
import site.omagotchi.learningservice.study.application.port.TimerRunRepository;
import site.omagotchi.learningservice.study.domain.TimerEndReason;
import site.omagotchi.learningservice.study.domain.TimerRun;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.TimerRunJpaPersistence;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.TimerRunQueryDslRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        TimerRunJpaPersistence.class,
        TimerRunQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("타이머 실행 저장소")
class TimerRunRepositoryIT {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2000-01-01T01:00:00Z");
    private static final TimerTimePolicy TIME_POLICY = new TimerTimePolicy(
            Duration.ofHours(12)
    );

    @Autowired
    private TimerRunRepository timerRunRepository;

    @Autowired
    private TimerRunQueryRepository timerRunQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpMemberships() {
        CohortMembershipTestFixture.ensureActiveMemberships(
                jdbcTemplate,
                COHORT_MEMBERSHIP_ID,
                102L,
                104L
        );
    }

    @Nested
    @DisplayName("실행 생명주기")
    class Lifecycle {

        @Test
        @DisplayName("생성부터 종료까지 정상 처리")
        void persistsActiveRunAndExcludesItFromActiveQueryAfterEnd() {
            TimerRun created = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );

            TimerRun active = timerRunQueryRepository
                    .findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertNotNull(active.getId()),
                    () -> assertEquals(created.getId(), active.getId()),
                    () -> assertEquals(STARTED_AT, active.getStartedAt()),
                    () -> assertNotNull(active.getCreatedAt()),
                    () -> assertTrue(active.isRunning())
            );

            active.stopOrExpire(ENDED_AT, TIME_POLICY);
            timerRunRepository.end(active);

            TimerRun ended = timerRunQueryRepository
                    .findOwnedById(active.getId(), COHORT_MEMBERSHIP_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertTrue(
                            timerRunQueryRepository
                                    .findActiveByCohortMembershipId(COHORT_MEMBERSHIP_ID)
                                    .isEmpty()
                    ),
                    () -> assertFalse(ended.isRunning()),
                    () -> assertEquals(ENDED_AT, ended.getEndedAt()),
                    () -> assertEquals(3_600L, ended.getMeasuredSeconds()),
                    () -> assertEquals(TimerEndReason.STOP, ended.getEndReason()),
                    () -> assertEquals(active.getCreatedAt(), ended.getCreatedAt())
            );
        }

        @Test
        @DisplayName("겹침 종료 상태 저장")
        void persistsOverlapEndState() {
            TimerRun timerRun = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );
            timerRun.stopOrExpire(ENDED_AT, TIME_POLICY);
            timerRun.rejectStudyRecordDueToOverlap();

            timerRunRepository.end(timerRun);

            TimerRun overlapped = timerRunQueryRepository
                    .findOwnedById(timerRun.getId(), COHORT_MEMBERSHIP_ID)
                    .orElseThrow();
            assertAll(
                    () -> assertEquals(ENDED_AT, overlapped.getEndedAt()),
                    () -> assertEquals(TimerEndReason.OVERLAP, overlapped.getEndReason()),
                    () -> assertNull(overlapped.getMeasuredSeconds())
            );
        }

        @Test
        @DisplayName("같은 기수의 여러 학생 멤버십에서 열린 타이머만 일괄 조회")
        void findsOpenRunsForStudentMembershipsOfSameCohort() {
            TimerRun firstActive = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );
            TimerRun secondActive = timerRunRepository.create(
                    TimerRun.start(102L, STARTED_AT.plusSeconds(1L))
            );
            TimerRun ended = timerRunRepository.create(
                    TimerRun.start(104L, STARTED_AT.plusSeconds(2L))
            );
            ended.stopOrExpire(ENDED_AT, TIME_POLICY);
            timerRunRepository.end(ended);

            List<TimerRun> results = timerRunQueryRepository
                    .findActiveByCohortMembershipIds(List.of(
                            104L,
                            102L,
                            COHORT_MEMBERSHIP_ID
                    ));

            assertAll(
                    () -> assertEquals(
                            List.of(firstActive.getId(), secondActive.getId()),
                            results.stream().map(TimerRun::getId).toList()
                    ),
                    () -> assertTrue(results.stream().allMatch(TimerRun::isRunning))
            );
        }
    }

    @Nested
    @DisplayName("시간 정밀도 제약")
    class TimePrecisionConstraint {

        @Test
        @DisplayName("초 미만 정밀도 저장 거절")
        void rejectsSubSecondPrecision() {
            String sql = """
                    INSERT INTO learning_service.timer_runs (
                        id,
                        cohort_membership_id,
                        started_at
                    ) VALUES (?, ?, ?)
                    """;
            UUID timerRunId = UUID.fromString("00000000-0000-0000-0000-000000000102");
            OffsetDateTime startedAt = OffsetDateTime.parse("2000-01-01T00:00:00.001Z");

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(sql, timerRunId, 102L, startedAt)
            );
        }
    }

    @Nested
    @DisplayName("소속 FK 제약")
    class MembershipConstraint {

        @Test
        @DisplayName("존재하지 않는 소속의 실행 저장 거절")
        void rejectsUnknownMembership() {
            String sql = """
                    INSERT INTO learning_service.timer_runs (
                        id,
                        cohort_membership_id,
                        started_at
                    ) VALUES (?, ?, ?)
                    """;
            UUID timerRunId = UUID.fromString("00000000-0000-0000-0000-000000000103");
            OffsetDateTime startedAt = OffsetDateTime.parse("2000-01-01T00:00:00Z");

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(sql, timerRunId, 103L, startedAt)
            );
        }
    }

    @Nested
    @DisplayName("실행 상태 제약")
    class StateConstraint {

        @Test
        @DisplayName("폐기 상태 저장")
        void persistsDiscardedState() {
            TimerRun timerRun = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );

            timerRun.discardOrExpire(ENDED_AT, TIME_POLICY);
            timerRunRepository.end(timerRun);

            TimerRun discarded = timerRunQueryRepository
                    .findOwnedById(timerRun.getId(), COHORT_MEMBERSHIP_ID)
                    .orElseThrow();
            assertAll(
                    () -> assertEquals(ENDED_AT, discarded.getEndedAt()),
                    () -> assertEquals(TimerEndReason.DISCARD, discarded.getEndReason()),
                    () -> assertNull(discarded.getMeasuredSeconds())
            );
        }

        @Test
        @DisplayName("만료 상태 저장")
        void persistsExpiredState() {
            TimerRun timerRun = timerRunRepository.create(
                    TimerRun.start(COHORT_MEMBERSHIP_ID, STARTED_AT)
            );
            Instant expiredAt = STARTED_AT.plus(Duration.ofHours(12));

            assertTrue(timerRun.expireIfDue(expiredAt, TIME_POLICY));
            timerRunRepository.end(timerRun);

            TimerRun expired = timerRunQueryRepository
                    .findOwnedById(timerRun.getId(), COHORT_MEMBERSHIP_ID)
                    .orElseThrow();
            assertAll(
                    () -> assertEquals(expiredAt, expired.getEndedAt()),
                    () -> assertEquals(TimerEndReason.EXPIRED, expired.getEndReason()),
                    () -> assertNull(expired.getMeasuredSeconds())
            );
        }

        @ParameterizedTest(name = "{0} 사유")
        @ValueSource(strings = {"STOP", "OVERLAP", "DISCARD", "EXPIRED"})
        @DisplayName("종료 시각 없는 종료 사유 저장 거절")
        void rejectsEndReasonWithoutEndTime(String endReason) {
            String sql = """
                    INSERT INTO learning_service.timer_runs (
                        id,
                        cohort_membership_id,
                        started_at,
                        end_reason
                    ) VALUES (?, ?, ?, ?)
                    """;
            UUID timerRunId = UUID.randomUUID();
            OffsetDateTime startedAt = OffsetDateTime.parse("2000-01-01T00:00:00Z");

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            sql,
                            timerRunId,
                            COHORT_MEMBERSHIP_ID,
                            startedAt,
                            endReason
                    )
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidEndStates")
        @DisplayName("유효하지 않은 종료 상태 저장 거절")
        void rejectsInvalidEndState(
                String ignoredDescription,
                OffsetDateTime endedAt,
                Long measuredSeconds,
                String endReason
        ) {
            String sql = """
                    INSERT INTO learning_service.timer_runs (
                        id,
                        cohort_membership_id,
                        started_at,
                        ended_at,
                        measured_seconds,
                        end_reason
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """;
            UUID timerRunId = UUID.fromString("00000000-0000-0000-0000-000000000104");
            OffsetDateTime startedAt = OffsetDateTime.parse("2000-01-01T00:00:00Z");

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            sql,
                            timerRunId,
                            COHORT_MEMBERSHIP_ID,
                            startedAt,
                            endedAt,
                            measuredSeconds,
                            endReason
                    )
            );
        }

        private static Stream<Arguments> invalidEndStates() {
            OffsetDateTime oneMinuteAfterStart = OffsetDateTime.parse("2000-01-01T00:01:00Z");
            OffsetDateTime oneHourAfterStart = OffsetDateTime.parse("2000-01-01T01:00:00Z");

            return Stream.of(
                    Arguments.of(
                            "계약에 없는 종료 사유",
                            oneHourAfterStart,
                            null,
                            "UNKNOWN"
                    ),
                    Arguments.of(
                            "종료 사유 없는 종료 실행",
                            oneHourAfterStart,
                            null,
                            null
                    ),
                    Arguments.of(
                            "측정 시간 없는 정지 상태",
                            oneHourAfterStart,
                            null,
                            "STOP"
                    ),
                    Arguments.of(
                            "경과 시간을 초과한 정지 측정 시간",
                            oneMinuteAfterStart,
                            3_600L,
                            "STOP"
                    )
            );
        }

        @ParameterizedTest(name = "{0} 상태")
        @ValueSource(strings = {"OVERLAP", "DISCARD", "EXPIRED"})
        @DisplayName("측정 시간 있는 미기록 종료 상태 저장 거절")
        void rejectsUnrecordedEndStateWithMeasuredSeconds(String endReason) {
            String sql = """
                    INSERT INTO learning_service.timer_runs (
                        id,
                        cohort_membership_id,
                        started_at,
                        ended_at,
                        measured_seconds,
                        end_reason
                    ) VALUES (?, ?, ?, ?, 3600, ?)
                    """;
            UUID timerRunId = UUID.randomUUID();
            OffsetDateTime startedAt = OffsetDateTime.parse("2000-01-01T00:00:00Z");
            OffsetDateTime endedAt = OffsetDateTime.parse("2000-01-01T01:00:00Z");

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            sql,
                            timerRunId,
                            COHORT_MEMBERSHIP_ID,
                            startedAt,
                            endedAt,
                            endReason
                    )
            );
        }
    }
}
