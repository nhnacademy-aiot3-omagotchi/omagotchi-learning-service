package site.omagotchi.learningservice.statistics.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.TodaySummary;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({
        TestcontainersConfiguration.class,
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        CohortStatisticsQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("기수 학습 통계")
class CohortStatisticsQueryDslRepositoryIT {

    private static final UUID CREATED_BY_USER_ID = new UUID(0L, 1L);
    private static final LocalDate PERIOD_FROM = LocalDate.parse("2000-01-01");
    private static final LocalDate AGGREGATION_DATE = LocalDate.parse("2000-01-02");
    private static final LocalDate PERIOD_TO = LocalDate.parse("2000-01-03");
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRecordJpaRepository studyRecordJpaRepository;

    @Autowired
    private CohortStatisticsQueryDslRepository queryRepository;

    @Nested
    @DisplayName("오늘 통계 집계")
    class SummarizeToday {

        @Test
        @DisplayName("활성 수강생의 확정 기록만 시간 구간별 집계 정상 처리")
        void aggregatesOnlyConfirmedRecordsOfActiveStudents() {
            Long cohortId = insertCohort("집계 대상 기수");
            Long otherCohortId = insertCohort("다른 기수");
            Long noRecordStudentId = insertActiveStudent(cohortId, 1_001L);
            Long underOneHourStudentId = insertActiveStudent(cohortId, 1_002L);
            Long oneToTwoHoursStudentId = insertActiveStudent(cohortId, 1_003L);
            Long twoToFourHoursStudentId = insertActiveStudent(cohortId, 1_004L);
            Long fourHoursOrMoreStudentId = insertActiveStudent(cohortId, 1_005L);
            Long mentorId = insertActiveMentor(cohortId, 1_006L);
            Long endedStudentId = insertEndedStudent(cohortId, 1_007L);
            Long otherCohortStudentId = insertActiveStudent(otherCohortId, 1_008L);

            saveDeletedRecord(
                    noRecordStudentId,
                    "2000-01-02T05:00:00Z",
                    3_600L,
                    "2000-01-03T00:00:00Z"
            );
            saveAggregationRecord(
                    underOneHourStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L,
                    1_800L
            );
            saveAggregationRecord(
                    underOneHourStudentId,
                    "2000-01-01T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    oneToTwoHoursStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    twoToFourHoursStudentId,
                    "2000-01-02T00:00:00Z",
                    7_200L
            );
            saveAggregationRecord(
                    fourHoursOrMoreStudentId,
                    "2000-01-02T00:00:00Z",
                    14_400L
            );
            saveAggregationRecord(
                    mentorId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    endedStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    otherCohortStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            studyRecordJpaRepository.flush();

            TodaySummary result = queryRepository.summarizeToday(
                    cohortId,
                    AGGREGATION_DATE
            );

            assertEquals(
                    new TodaySummary(
                            27_000L,
                            5L,
                            4L,
                            1L,
                            List.of(
                                    new DurationBucketResult("NO_RECORD", 1L),
                                    new DurationBucketResult("UNDER_ONE_HOUR", 1L),
                                    new DurationBucketResult("ONE_TO_TWO_HOURS", 1L),
                                    new DurationBucketResult("TWO_TO_FOUR_HOURS", 1L),
                                    new DurationBucketResult("FOUR_HOURS_OR_MORE", 1L)
                            )
                    ),
                    result
            );
        }

        @Test
        @DisplayName("활성 수강생이 없으면 모든 집계 0초 정상 처리")
        void returnsZerosWhenCohortHasNoActiveStudents() {
            Long cohortId = insertCohort("빈 기수");

            TodaySummary result = queryRepository.summarizeToday(
                    cohortId,
                    AGGREGATION_DATE
            );

            assertEquals(
                    new TodaySummary(
                            0L,
                            0L,
                            0L,
                            0L,
                            List.of(
                                    new DurationBucketResult("NO_RECORD", 0L),
                                    new DurationBucketResult("UNDER_ONE_HOUR", 0L),
                                    new DurationBucketResult("ONE_TO_TWO_HOURS", 0L),
                                    new DurationBucketResult("TWO_TO_FOUR_HOURS", 0L),
                                    new DurationBucketResult("FOUR_HOURS_OR_MORE", 0L)
                            )
                    ),
                    result
            );
        }
    }

    @Nested
    @DisplayName("일별 학습시간 조회")
    class FindDailyStudySeconds {

        @Test
        @DisplayName("기간 내 활성 수강생 기록을 날짜별 집계 정상 처리")
        void aggregatesDailyStudySecondsOfActiveStudents() {
            Long cohortId = insertCohort("기간 집계 대상 기수");
            Long otherCohortId = insertCohort("기간 집계 다른 기수");
            Long firstStudentId = insertActiveStudent(cohortId, 1_101L);
            Long secondStudentId = insertActiveStudent(cohortId, 1_102L);
            Long mentorId = insertActiveMentor(cohortId, 1_103L);
            Long endedStudentId = insertEndedStudent(cohortId, 1_104L);
            Long otherCohortStudentId = insertActiveStudent(otherCohortId, 1_105L);

            saveAggregationRecord(
                    firstStudentId,
                    "2000-01-01T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    firstStudentId,
                    "2000-01-01T01:00:00Z",
                    1_800L
            );
            saveAggregationRecord(
                    secondStudentId,
                    "2000-01-03T00:00:00Z",
                    7_200L
            );
            saveOutOfPeriodRecord(
                    firstStudentId,
                    "1999-12-31T00:00:00Z",
                    3_600L
            );
            saveDeletedRecord(
                    firstStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L,
                    "2000-01-04T00:00:00Z"
            );
            saveAggregationRecord(
                    mentorId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    endedStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            saveAggregationRecord(
                    otherCohortStudentId,
                    "2000-01-02T00:00:00Z",
                    3_600L
            );
            studyRecordJpaRepository.flush();

            List<DailyTotalResult> result = queryRepository.findDailyStudySeconds(
                    cohortId,
                    PERIOD_FROM,
                    PERIOD_TO
            );

            assertEquals(
                    List.of(
                            new DailyTotalResult(PERIOD_FROM, 5_400L),
                            new DailyTotalResult(PERIOD_TO, 7_200L)
                    ),
                    result
            );
        }
    }

    private Long insertCohort(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id
                ) VALUES (?, DATE '2000-01-01', DATE '2000-12-31', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, name, CREATED_BY_USER_ID);
    }

    private Long insertMembership(
            Long cohortId,
            long userNumber,
            CohortMembershipRole role,
            CohortMembershipStatus status
    ) {
        UUID userId = new UUID(0L, userNumber);
        OffsetDateTime endedAt = status == CohortMembershipStatus.ENDED
                ? PROCESSED_AT.plusDays(10)
                : null;
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    processed_at,
                    processed_by_user_id,
                    ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                cohortId,
                userId,
                role.name(),
                status.name(),
                PROCESSED_AT,
                userId,
                endedAt
        );
    }

    private Long insertActiveStudent(Long cohortId, long userNumber) {
        return insertMembership(
                cohortId,
                userNumber,
                CohortMembershipRole.STUDENT,
                CohortMembershipStatus.ACTIVE
        );
    }

    private Long insertActiveMentor(Long cohortId, long userNumber) {
        return insertMembership(
                cohortId,
                userNumber,
                CohortMembershipRole.MENTOR,
                CohortMembershipStatus.ACTIVE
        );
    }

    private Long insertEndedStudent(Long cohortId, long userNumber) {
        return insertMembership(
                cohortId,
                userNumber,
                CohortMembershipRole.STUDENT,
                CohortMembershipStatus.ENDED
        );
    }

    private StudyRecord saveAggregationRecord(
            Long cohortMembershipId,
            String startTime,
            long studySeconds
    ) {
        return saveAggregationRecord(
                cohortMembershipId,
                startTime,
                studySeconds,
                studySeconds
        );
    }

    private StudyRecord saveAggregationRecord(
            Long cohortMembershipId,
            String startTime,
            long occupiedSeconds,
            long studySeconds
    ) {
        Instant startedAt = Instant.parse(startTime);
        StudyRecord studyRecord = StudyRecord.create(
                cohortMembershipId,
                startedAt,
                startedAt.plusSeconds(occupiedSeconds),
                studySeconds
        );
        return studyRecordJpaRepository.save(studyRecord);
    }

    private void saveOutOfPeriodRecord(
            Long cohortMembershipId,
            String startTime,
            long studySeconds
    ) {
        saveAggregationRecord(cohortMembershipId, startTime, studySeconds);
    }

    private void saveDeletedRecord(
            Long cohortMembershipId,
            String startTime,
            long studySeconds,
            String deletedAt
    ) {
        StudyRecord studyRecord = saveAggregationRecord(
                cohortMembershipId,
                startTime,
                studySeconds
        );
        studyRecord.softDelete(Instant.parse(deletedAt));
    }
}
