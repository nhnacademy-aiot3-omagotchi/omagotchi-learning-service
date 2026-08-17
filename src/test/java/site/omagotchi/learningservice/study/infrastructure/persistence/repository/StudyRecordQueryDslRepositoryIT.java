package site.omagotchi.learningservice.study.infrastructure.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        StudyRecordQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QueryDSL 멤버십별 확정 공부시간 조회")
class StudyRecordQueryDslRepositoryIT {

    private static final UUID CREATED_BY_USER_ID = new UUID(0L, 1L);
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.parse(
            "2000-01-01T00:00:00Z"
    );
    private static final LocalDate START_DATE = LocalDate.parse("2000-01-03");
    private static final LocalDate END_DATE = LocalDate.parse("2000-01-07");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRecordQueryDslRepository repository;

    private long recordNumber;

    @BeforeEach
    void resetRecordNumber() {
        recordNumber = 100L;
    }

    @Test
    @DisplayName("기간 내 삭제되지 않은 study_records만 멤버십별 합산")
    void sumsOnlyConfirmedRecordsInPeriod() {
        Long cohortId = insertCohort();
        Long firstMembershipId = insertMembership(cohortId, 10L);
        Long secondMembershipId = insertMembership(cohortId, 11L);
        Long timerOnlyMembershipId = insertMembership(cohortId, 12L);
        insertStudyRecord(firstMembershipId, "2000-01-04", 7_200L, false);
        insertStudyRecord(firstMembershipId, "2000-01-05", 1_800L, false);
        insertStudyRecord(firstMembershipId, "2000-01-02", 9_000L, false);
        insertStudyRecord(firstMembershipId, "2000-01-05", 9_000L, true);
        insertStudyRecord(secondMembershipId, "2000-01-06", 3_600L, false);
        insertActiveTimerRun(timerOnlyMembershipId);

        List<MemberStudyDurationResult> durations = repository.findConfirmedDurations(
                List.of(firstMembershipId, secondMembershipId, timerOnlyMembershipId),
                START_DATE,
                END_DATE
        );

        assertEquals(
                List.of(
                        new MemberStudyDurationResult(firstMembershipId, 9_000L),
                        new MemberStudyDurationResult(secondMembershipId, 3_600L)
                ),
                durations
        );
    }

    private Long insertCohort() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id
                ) VALUES ('공부시간 집계 기수', DATE '2000-01-01', DATE '2000-12-31', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, CREATED_BY_USER_ID);
    }

    private Long insertMembership(Long cohortId, long userNumber) {
        UUID userId = new UUID(0L, userNumber);
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    processed_at,
                    processed_by_user_id
                ) VALUES (?, ?, 'STUDENT', 'ACTIVE', ?, ?)
                RETURNING id
                """,
                Long.class,
                cohortId,
                userId,
                PROCESSED_AT,
                userId
        );
    }

    private void insertStudyRecord(
            Long cohortMembershipId,
            String aggregationDate,
            long studySeconds,
            boolean deleted
    ) {
        recordNumber++;
        OffsetDateTime startTime = OffsetDateTime.parse(aggregationDate + "T00:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO learning_service.study_records (
                    id,
                    cohort_membership_id,
                    aggregation_date,
                    start_time,
                    end_time,
                    study_seconds,
                    deleted_at
                ) VALUES (?, ?, ?::DATE, ?, ?, ?, ?)
                """,
                new UUID(1L, recordNumber),
                cohortMembershipId,
                aggregationDate,
                startTime,
                startTime.plusHours(3),
                studySeconds,
                deleted ? OffsetDateTime.parse("2000-01-06T00:00:00Z") : null
        );
    }

    private void insertActiveTimerRun(Long cohortMembershipId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.timer_runs (
                    id,
                    cohort_membership_id,
                    started_at
                ) VALUES (?, ?, ?)
                """,
                new UUID(2L, 1L),
                cohortMembershipId,
                OffsetDateTime.parse("2000-01-07T00:00:00Z")
        );
    }
}
