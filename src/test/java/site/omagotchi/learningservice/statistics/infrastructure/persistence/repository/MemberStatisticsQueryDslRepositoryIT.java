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
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Import({
        TestcontainersConfiguration.class,
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        MemberStatisticsQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("수강생 통계 페이지 PostgreSQL 조회")
class MemberStatisticsQueryDslRepositoryIT {

    private static final LocalDate CURRENT_AGGREGATION_DATE = LocalDate.of(
            2000,
            Month.JANUARY,
            30
    );
    private static final LocalDate FROM = LocalDate.of(2000, Month.JANUARY, 1);
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
    private MemberStatisticsQueryDslRepository queryRepository;

    @Test
    @DisplayName("활성 수강생 통계를 안정 정렬하여 DB 페이지로 반환")
    void returnsStableDatabasePagesOfActiveStudentStatistics() {
        Long cohortId = insertCohort("페이지 집계 대상 기수");
        Long otherCohortId = insertCohort("페이지 집계 다른 기수");
        Long firstStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long secondStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long thirdStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long fourthStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long fifthStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long mentorId = insertMembership(cohortId, "MENTOR", "ACTIVE");
        Long endedStudentId = insertMembership(cohortId, "STUDENT", "ENDED");
        Long otherCohortStudentId = insertMembership(otherCohortId, "STUDENT", "ACTIVE");

        saveRecord(firstStudentId, "2000-01-01T00:00:00Z", "2000-01-01T01:00:00Z", 3_600L);
        saveRecord(firstStudentId, "2000-01-30T00:00:00Z", "2000-01-30T01:00:00Z", 3_600L);
        saveRecord(secondStudentId, "2000-01-02T00:00:00Z", "2000-01-02T02:00:00Z", 7_200L);
        saveRecord(thirdStudentId, "2000-01-03T00:00:00Z", "2000-01-03T01:00:00Z", 3_600L);
        saveRecord(fourthStudentId, "1999-12-31T00:00:00Z", "1999-12-31T01:00:00Z", 3_600L);
        StudyRecord deleted = saveRecord(
                fifthStudentId,
                "2000-01-04T00:00:00Z",
                "2000-01-04T01:00:00Z",
                3_600L
        );
        deleted.softDelete(Instant.parse("2000-01-05T00:00:00Z"));
        studyRecordJpaRepository.saveAndFlush(deleted);
        saveRecord(mentorId, "2000-01-05T00:00:00Z", "2000-01-05T01:00:00Z", 3_600L);
        saveRecord(endedStudentId, "2000-01-05T00:00:00Z", "2000-01-05T01:00:00Z", 3_600L);
        saveRecord(otherCohortStudentId, "2000-01-05T00:00:00Z", "2000-01-05T01:00:00Z", 3_600L);

        List<MemberSummaryResult> firstPage = findPage(cohortId, 0, 2);
        List<MemberSummaryResult> secondPage = findPage(cohortId, 1, 2);
        List<MemberSummaryResult> thirdPage = findPage(cohortId, 2, 2);
        long totalElements = queryRepository.countActiveStudents(cohortId);

        assertAll(
                () -> assertEquals(List.of(firstStudentId, secondStudentId), membershipIds(firstPage)),
                () -> assertEquals(List.of(thirdStudentId, fourthStudentId), membershipIds(secondPage)),
                () -> assertEquals(List.of(fifthStudentId), membershipIds(thirdPage)),
                () -> assertEquals(5L, totalElements),
                () -> assertEquals(3_600L, firstPage.getFirst().todayStudySeconds()),
                () -> assertEquals(7_200L, firstPage.getFirst().periodStudySeconds()),
                () -> assertEquals(2L, firstPage.getFirst().activeStudyDays()),
                () -> assertEquals(2L, firstPage.getFirst().recordCount()),
                () -> assertEquals(
                        Instant.parse("2000-01-30T01:00:00Z"),
                        firstPage.getFirst().lastStudiedAt()
                ),
                () -> assertEquals(0L, secondPage.getLast().periodStudySeconds()),
                () -> assertEquals(0L, secondPage.getLast().activeStudyDays()),
                () -> assertEquals(0L, secondPage.getLast().recordCount()),
                () -> assertNull(secondPage.getLast().lastStudiedAt()),
                () -> assertEquals(0L, thirdPage.getFirst().periodStudySeconds()),
                () -> assertNull(thirdPage.getFirst().lastStudiedAt()),
                () -> assertEquals(
                        List.of(
                                fourthStudentId,
                                fifthStudentId,
                                thirdStudentId,
                                firstStudentId,
                                secondStudentId
                        ),
                        membershipIds(findAll(cohortId, "periodStudySeconds,asc"))
                ),
                () -> assertEquals(
                        List.of(
                                firstStudentId,
                                secondStudentId,
                                thirdStudentId,
                                fourthStudentId,
                                fifthStudentId
                        ),
                        membershipIds(findAll(cohortId, "todayStudySeconds,desc"))
                ),
                () -> assertEquals(
                        List.of(
                                firstStudentId,
                                secondStudentId,
                                thirdStudentId,
                                fourthStudentId,
                                fifthStudentId
                        ),
                        membershipIds(findAll(cohortId, "activeStudyDays,desc"))
                ),
                () -> assertEquals(
                        List.of(
                                firstStudentId,
                                secondStudentId,
                                thirdStudentId,
                                fourthStudentId,
                                fifthStudentId
                        ),
                        membershipIds(findAll(cohortId, "recordCount,desc"))
                ),
                () -> assertEquals(
                        List.of(
                                firstStudentId,
                                thirdStudentId,
                                secondStudentId,
                                fourthStudentId,
                                fifthStudentId
                        ),
                        membershipIds(findAll(cohortId, "lastStudiedAt,desc"))
                ),
                () -> assertEquals(
                        List.of(
                                fifthStudentId,
                                fourthStudentId,
                                thirdStudentId,
                                secondStudentId,
                                firstStudentId
                        ),
                        membershipIds(findAll(cohortId, "cohortMembershipId,desc"))
                )
        );
    }

    @Test
    @DisplayName("같은 기수 활성 수강생의 기간 overview를 집계")
    void summarizesOverviewOfActiveStudentInCohort() {
        Long cohortId = insertCohort("overview 대상 기수");
        Long otherCohortId = insertCohort("overview 다른 기수");
        UUID targetUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        Long targetStudentId = insertMembership(
                cohortId,
                targetUserId,
                "STUDENT",
                "ACTIVE"
        );
        Long mentorId = insertMembership(cohortId, "MENTOR", "ACTIVE");
        Long endedStudentId = insertMembership(cohortId, "STUDENT", "ENDED");
        Long otherCohortStudentId = insertMembership(
                otherCohortId,
                "STUDENT",
                "ACTIVE"
        );
        LocalDate from = LocalDate.of(2000, Month.JANUARY, 24);

        saveRecord(
                targetStudentId,
                "2000-01-24T00:00:00Z",
                "2000-01-24T01:00:00Z",
                3_600L
        );
        saveRecord(
                targetStudentId,
                "2000-01-24T01:00:00Z",
                "2000-01-24T01:30:00Z",
                1_800L
        );
        saveRecord(
                targetStudentId,
                "2000-01-30T00:00:00Z",
                "2000-01-30T02:00:00Z",
                7_200L
        );
        saveRecord(
                targetStudentId,
                "2000-01-23T00:00:00Z",
                "2000-01-23T01:00:00Z",
                3_600L
        );
        StudyRecord deleted = saveRecord(
                targetStudentId,
                "2000-01-25T00:00:00Z",
                "2000-01-25T01:00:00Z",
                3_600L
        );
        deleted.softDelete(Instant.parse("2000-01-26T00:00:00Z"));
        studyRecordJpaRepository.saveAndFlush(deleted);

        MemberReference memberReference = queryRepository.findActiveStudent(
                cohortId,
                targetStudentId
        ).orElseThrow();
        PeriodSummary summary = queryRepository
                .summarizeActiveRecords(targetStudentId, from, CURRENT_AGGREGATION_DATE);
        List<DailyTotalResult> dailyTotals = queryRepository
                .findMemberDailyStudySeconds(
                        targetStudentId,
                        from,
                        CURRENT_AGGREGATION_DATE
                );

        assertAll(
                () -> assertEquals(targetStudentId, memberReference.cohortMembershipId()),
                () -> assertEquals(targetUserId, memberReference.userId()),
                () -> assertEquals(12_600L, summary.totalStudySeconds()),
                () -> assertEquals(2L, summary.activeStudyDays()),
                () -> assertEquals(3L, summary.recordCount()),
                () -> assertEquals(
                        Instant.parse("2000-01-30T02:00:00Z"),
                        summary.lastStudiedAt()
                ),
                () -> assertEquals(
                        List.of(
                                new DailyTotalResult(from, 5_400L),
                                new DailyTotalResult(
                                        CURRENT_AGGREGATION_DATE,
                                        7_200L
                                )
                        ),
                        dailyTotals
                ),
                () -> assertEquals(
                        Optional.empty(),
                        queryRepository.findActiveStudent(cohortId, mentorId)
                ),
                () -> assertEquals(
                        Optional.empty(),
                        queryRepository.findActiveStudent(cohortId, endedStudentId)
                ),
                () -> assertEquals(
                        Optional.empty(),
                        queryRepository.findActiveStudent(cohortId, otherCohortStudentId)
                )
        );
    }

    @Test
    @DisplayName("선택 집계일의 삭제되지 않은 수강생 기록만 안정 정렬")
    void returnsOnlyActiveMemberRecordsOfSelectedAggregationDate() {
        Long cohortId = insertCohort("daily records 대상 기수");
        Long targetStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        Long otherStudentId = insertMembership(cohortId, "STUDENT", "ACTIVE");
        LocalDate selectedDate = LocalDate.of(2000, Month.JANUARY, 30);
        StudyRecord secondRecord = saveRecord(
                targetStudentId,
                "2000-01-30T02:00:00Z",
                "2000-01-30T03:00:00Z",
                3_600L
        );
        StudyRecord firstRecord = saveRecord(
                targetStudentId,
                "2000-01-30T00:00:00Z",
                "2000-01-30T01:30:00Z",
                5_400L
        );
        saveRecord(
                targetStudentId,
                "2000-01-29T00:00:00Z",
                "2000-01-29T01:00:00Z",
                3_600L
        );
        StudyRecord deleted = saveRecord(
                targetStudentId,
                "2000-01-30T04:00:00Z",
                "2000-01-30T05:00:00Z",
                3_600L
        );
        deleted.softDelete(Instant.parse("2000-01-30T06:00:00Z"));
        studyRecordJpaRepository.saveAndFlush(deleted);
        saveRecord(
                otherStudentId,
                "2000-01-30T00:00:00Z",
                "2000-01-30T01:00:00Z",
                3_600L
        );

        List<MemberDailyRecordResult> records = queryRepository
                .findMemberDailyRecords(targetStudentId, selectedDate);

        assertAll(
                () -> assertEquals(2, records.size()),
                () -> assertEquals(firstRecord.getId(), records.getFirst().id()),
                () -> assertEquals(
                        Instant.parse("2000-01-30T00:00:00Z"),
                        records.getFirst().startTime()
                ),
                () -> assertEquals(
                        Instant.parse("2000-01-30T01:30:00Z"),
                        records.getFirst().endTime()
                ),
                () -> assertEquals(5_400L, records.getFirst().studySeconds()),
                () -> assertEquals(secondRecord.getId(), records.getLast().id()),
                () -> assertEquals(
                        Instant.parse("2000-01-30T02:00:00Z"),
                        records.getLast().startTime()
                ),
                () -> assertEquals(3_600L, records.getLast().studySeconds())
        );
    }

    private List<MemberSummaryResult> findPage(
            Long cohortId,
            int page,
            int size
    ) {
        return queryRepository.findActiveStudentStatisticsPage(
                cohortId,
                CURRENT_AGGREGATION_DATE,
                FROM,
                CURRENT_AGGREGATION_DATE,
                MemberPageQuery.of(
                        "30d",
                        page,
                        size,
                        "periodStudySeconds,desc"
                )
        );
    }

    private List<Long> membershipIds(List<MemberSummaryResult> items) {
        return items.stream()
                .map(MemberSummaryResult::cohortMembershipId)
                .toList();
    }

    private List<MemberSummaryResult> findAll(Long cohortId, String sort) {
        return queryRepository.findActiveStudentStatisticsPage(
                cohortId,
                CURRENT_AGGREGATION_DATE,
                FROM,
                CURRENT_AGGREGATION_DATE,
                MemberPageQuery.of("30d", 0, 100, sort)
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
        return insertMembership(cohortId, UUID.randomUUID(), role, status);
    }

    private Long insertMembership(
            Long cohortId,
            UUID userId,
            String role,
            String status
    ) {
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
        StudyRecord record = StudyRecord.create(
                cohortMembershipId,
                Instant.parse(startTime),
                Instant.parse(endTime),
                studySeconds
        );
        return studyRecordJpaRepository.saveAndFlush(record);
    }
}
