package site.omagotchi.learningservice.statistics.infrastructure.persistence.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.TodaySummary;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
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
@DisplayName("관리자 학습 통계 PostgreSQL 조회")
class CohortStatisticsQueryDslRepositoryIT {

    private static final LocalDate AGGREGATION_DATE = LocalDate.of(
            2000,
            Month.JANUARY,
            2
    );
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.of(
            2000,
            Month.JANUARY.getValue(),
            1,
            0,
            0,
            0,
            0,
            ZoneOffset.UTC
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRecordJpaRepository studyRecordJpaRepository;

    @Autowired
    private CohortStatisticsQueryDslRepository queryRepository;

    @Test
    @DisplayName("활성 수강생의 확정 기록만 시간 구간별 집계")
    void aggregatesOnlyConfirmedRecordsOfActiveStudents() {
        Long cohortId = insertCohort("집계 대상 기수");
        Long otherCohortId = insertCohort("다른 기수");
        Long noRecordStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long underOneHourStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long oneToTwoHoursStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long twoToFourHoursStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long fourHoursOrMoreStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long mentorId = insertMembership(cohortId, "MENTOR", "ACTIVE");
        Long endedStudentId = insertMembership(cohortId, "STUDENT", "ENDED");
        Long otherCohortStudentId = insertMembership(otherCohortId, "STUDENT", "ACTIVE");

        StudyRecord deleted = saveRecord(
                noRecordStudentId,
                "2000-01-02T05:00:00Z",
                "2000-01-02T06:00:00Z",
                3_600L
        );
        deleted.softDelete(Instant.parse("2000-01-03T00:00:00Z"));
        studyRecordJpaRepository.saveAndFlush(deleted);
        saveRecord(
                underOneHourStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                1_800L
        );
        saveRecord(
                underOneHourStudentId,
                "2000-01-01T00:00:00Z",
                "2000-01-01T01:00:00Z",
                3_600L
        );
        saveRecord(
                oneToTwoHoursStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        saveRecord(
                twoToFourHoursStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T02:00:00Z",
                7_200L
        );
        saveRecord(
                fourHoursOrMoreStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T04:00:00Z",
                14_400L
        );
        saveRecord(
                mentorId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        saveRecord(
                endedStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        saveRecord(
                otherCohortStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );

        TodaySummary result = queryRepository.summarizeToday(
                cohortId,
                AGGREGATION_DATE
        );

        assertAll(
                () -> assertEquals(27_000L, result.totalStudySeconds()),
                () -> assertEquals(5L, result.activeStudentCount()),
                () -> assertEquals(4L, result.participantCount()),
                () -> assertEquals(1L, result.noRecordStudentCount()),
                () -> assertEquals(
                        List.of(
                                "NO_RECORD",
                                "UNDER_ONE_HOUR",
                                "ONE_TO_TWO_HOURS",
                                "TWO_TO_FOUR_HOURS",
                                "FOUR_HOURS_OR_MORE"
                        ),
                        result.durationBuckets().stream()
                                .map(DurationBucketResult::code)
                                .toList()
                ),
                () -> assertEquals(
                        List.of(1L, 1L, 1L, 1L, 1L),
                        result.durationBuckets().stream()
                                .map(DurationBucketResult::memberCount)
                                .toList()
                )
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

        assertAll(
                () -> assertEquals(0L, result.totalStudySeconds()),
                () -> assertEquals(0L, result.activeStudentCount()),
                () -> assertEquals(0L, result.participantCount()),
                () -> assertEquals(0L, result.noRecordStudentCount()),
                () -> assertEquals(
                        List.of(0L, 0L, 0L, 0L, 0L),
                        result.durationBuckets().stream()
                                .map(DurationBucketResult::memberCount)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("기간 내 활성 수강생 기록을 날짜별로 집계")
    void aggregatesDailyStudySecondsOfActiveStudents() {
        Long cohortId = insertCohort("기간 집계 대상 기수");
        Long otherCohortId = insertCohort("기간 집계 다른 기수");
        Long firstStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long secondStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long mentorId = insertMembership(cohortId, "MENTOR", "ACTIVE");
        Long endedStudentId = insertMembership(cohortId, "STUDENT", "ENDED");
        Long otherCohortStudentId = insertMembership(otherCohortId, "STUDENT", "ACTIVE");

        saveRecord(
                firstStudentId,
                "2000-01-01T00:00:00Z",
                "2000-01-01T01:00:00Z",
                3_600L
        );
        saveRecord(
                firstStudentId,
                "2000-01-01T01:00:00Z",
                "2000-01-01T01:30:00Z",
                1_800L
        );
        saveRecord(
                secondStudentId,
                "2000-01-03T00:00:00Z",
                "2000-01-03T02:00:00Z",
                7_200L
        );
        saveRecord(
                firstStudentId,
                "1999-12-31T00:00:00Z",
                "1999-12-31T01:00:00Z",
                3_600L
        );
        StudyRecord deleted = saveRecord(
                firstStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        deleted.softDelete(Instant.parse("2000-01-04T00:00:00Z"));
        studyRecordJpaRepository.saveAndFlush(deleted);
        saveRecord(
                mentorId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        saveRecord(
                endedStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );
        saveRecord(
                otherCohortStudentId,
                "2000-01-02T00:00:00Z",
                "2000-01-02T01:00:00Z",
                3_600L
        );

        List<DailyTotalResult> result = queryRepository.findDailyStudySeconds(
                cohortId,
                LocalDate.of(2000, Month.JANUARY, 1),
                LocalDate.of(2000, Month.JANUARY, 3)
        );

        assertEquals(
                List.of(
                        new DailyTotalResult(
                                LocalDate.of(2000, Month.JANUARY, 1),
                                5_400L
                        ),
                        new DailyTotalResult(
                                LocalDate.of(2000, Month.JANUARY, 3),
                                7_200L
                        )
                ),
                result
        );
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
                """, Long.class, name, UUID.randomUUID());
    }

    private Long insertMembership(Long cohortId, String role, String status) {
        UUID userId = UUID.randomUUID();
        OffsetDateTime endedAt = "ENDED".equals(status) ? PROCESSED_AT.plusDays(10) : null;
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
                role,
                status,
                PROCESSED_AT,
                userId,
                endedAt
        );
    }

    private StudyRecord saveRecord(
            Long cohortMembershipId,
            String startTime,
            String endTime,
            long studySeconds
    ) {
        StudyRecord studyRecord = StudyRecord.create(
                cohortMembershipId,
                Instant.parse(startTime),
                Instant.parse(endTime),
                studySeconds
        );
        return studyRecordJpaRepository.saveAndFlush(studyRecord);
    }
}
