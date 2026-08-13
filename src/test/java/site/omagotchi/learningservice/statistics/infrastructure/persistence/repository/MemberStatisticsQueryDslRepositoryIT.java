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
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;
import site.omagotchi.learningservice.study.infrastructure.persistence.repository.StudyRecordJpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import({
        TestcontainersConfiguration.class,
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        MemberStatisticsQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("수강생 학습 통계")
class MemberStatisticsQueryDslRepositoryIT {

    private static final UUID CREATED_BY_USER_ID = new UUID(0L, 2L);
    private static final LocalDate PAGE_FROM = LocalDate.parse("2000-01-01");
    private static final LocalDate PERIOD_FROM = LocalDate.parse("2000-01-24");
    private static final LocalDate CURRENT_AGGREGATION_DATE = LocalDate.parse("2000-01-30");
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.parse("2000-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRecordJpaRepository studyRecordJpaRepository;

    @Autowired
    private MemberStatisticsQueryDslRepository queryRepository;

    @Nested
    @DisplayName("수강생 통계 목록 조회")
    class FindActiveStudentStatisticsPage {

        @Test
        @DisplayName("활성 수강생만 안정 페이지 집계 정상 처리")
        void returnsStablePagesOfActiveStudentStatistics() {
            PageFixture fixture = createPageFixture(2_000L);
            studyRecordJpaRepository.flush();

            List<MemberSummaryResult> firstPage = findPage(fixture.cohortId(), 0, 2);
            List<MemberSummaryResult> secondPage = findPage(fixture.cohortId(), 1, 2);
            List<MemberSummaryResult> thirdPage = findPage(fixture.cohortId(), 2, 2);

            assertAll(
                    () -> assertEquals(
                            List.of(
                                    fixture.todayAndPeriodStudentId(),
                                    fixture.highPeriodStudentId()
                            ),
                            membershipIds(firstPage)
                    ),
                    () -> assertEquals(
                            List.of(
                                    fixture.periodStudentId(),
                                    fixture.outOfPeriodStudentId()
                            ),
                            membershipIds(secondPage)
                    ),
                    () -> assertEquals(
                            List.of(fixture.deletedRecordStudentId()),
                            membershipIds(thirdPage)
                    ),
                    () -> assertEquals(
                            new MemberSummaryResult(
                                    fixture.todayAndPeriodStudentId(),
                                    userId(2_001L),
                                    3_600L,
                                    7_200L,
                                    2L,
                                    2L,
                                    Instant.parse("2000-01-30T01:00:00Z")
                            ),
                            firstPage.getFirst()
                    ),
                    () -> assertEquals(
                            emptyMemberSummary(
                                    fixture.outOfPeriodStudentId(),
                                    userId(2_004L)
                            ),
                            secondPage.getLast()
                    ),
                    () -> assertEquals(
                            emptyMemberSummary(
                                    fixture.deletedRecordStudentId(),
                                    userId(2_005L)
                            ),
                            thirdPage.getFirst()
                    )
            );
        }

        @Test
        @DisplayName("지원 정렬 조건별 안정 정렬 정상 처리")
        void sortsBySupportedFieldsWithStableTieBreak() {
            PageFixture fixture = createPageFixture(2_100L);
            studyRecordJpaRepository.flush();

            List<Long> periodStudySecondsAsc = membershipIds(
                    findAll(fixture.cohortId(), "periodStudySeconds,asc")
            );
            List<Long> todayStudySecondsDesc = membershipIds(
                    findAll(fixture.cohortId(), "todayStudySeconds,desc")
            );
            List<Long> activeStudyDaysDesc = membershipIds(
                    findAll(fixture.cohortId(), "activeStudyDays,desc")
            );
            List<Long> recordCountDesc = membershipIds(
                    findAll(fixture.cohortId(), "recordCount,desc")
            );
            List<Long> lastStudiedAtDesc = membershipIds(
                    findAll(fixture.cohortId(), "lastStudiedAt,desc")
            );
            List<Long> cohortMembershipIdDesc = membershipIds(
                    findAll(fixture.cohortId(), "cohortMembershipId,desc")
            );
            List<Long> aggregateDescendingOrder = List.of(
                    fixture.todayAndPeriodStudentId(),
                    fixture.highPeriodStudentId(),
                    fixture.periodStudentId(),
                    fixture.outOfPeriodStudentId(),
                    fixture.deletedRecordStudentId()
            );

            assertAll(
                    () -> assertEquals(
                            List.of(
                                    fixture.outOfPeriodStudentId(),
                                    fixture.deletedRecordStudentId(),
                                    fixture.periodStudentId(),
                                    fixture.todayAndPeriodStudentId(),
                                    fixture.highPeriodStudentId()
                            ),
                            periodStudySecondsAsc
                    ),
                    () -> assertEquals(aggregateDescendingOrder, todayStudySecondsDesc),
                    () -> assertEquals(aggregateDescendingOrder, activeStudyDaysDesc),
                    () -> assertEquals(aggregateDescendingOrder, recordCountDesc),
                    () -> assertEquals(
                            List.of(
                                    fixture.todayAndPeriodStudentId(),
                                    fixture.periodStudentId(),
                                    fixture.highPeriodStudentId(),
                                    fixture.outOfPeriodStudentId(),
                                    fixture.deletedRecordStudentId()
                            ),
                            lastStudiedAtDesc
                    ),
                    () -> assertEquals(
                            List.of(
                                    fixture.deletedRecordStudentId(),
                                    fixture.outOfPeriodStudentId(),
                                    fixture.periodStudentId(),
                                    fixture.highPeriodStudentId(),
                                    fixture.todayAndPeriodStudentId()
                            ),
                            cohortMembershipIdDesc
                    )
            );
        }

        private PageFixture createPageFixture(long userNumberBase) {
            Long cohortId = insertCohort("페이지 집계 대상 기수");
            Long otherCohortId = insertCohort("페이지 집계 다른 기수");
            Long todayAndPeriodStudentId = insertActiveStudent(cohortId, userNumberBase + 1);
            Long highPeriodStudentId = insertActiveStudent(cohortId, userNumberBase + 2);
            Long periodStudentId = insertActiveStudent(cohortId, userNumberBase + 3);
            Long outOfPeriodStudentId = insertActiveStudent(cohortId, userNumberBase + 4);
            Long deletedRecordStudentId = insertActiveStudent(cohortId, userNumberBase + 5);
            Long mentorId = insertActiveMentor(cohortId, userNumberBase + 6);
            Long endedStudentId = insertEndedStudent(cohortId, userNumberBase + 7);
            Long otherCohortStudentId = insertActiveStudent(
                    otherCohortId,
                    userNumberBase + 8
            );

            saveRecord(
                    todayAndPeriodStudentId,
                    "2000-01-01T00:00:00Z",
                    "2000-01-01T01:00:00Z",
                    3_600L
            );
            saveRecord(
                    todayAndPeriodStudentId,
                    "2000-01-30T00:00:00Z",
                    "2000-01-30T01:00:00Z",
                    3_600L
            );
            saveRecord(
                    highPeriodStudentId,
                    "2000-01-02T00:00:00Z",
                    "2000-01-02T02:00:00Z",
                    7_200L
            );
            saveRecord(
                    periodStudentId,
                    "2000-01-03T00:00:00Z",
                    "2000-01-03T01:00:00Z",
                    3_600L
            );
            saveRecord(
                    outOfPeriodStudentId,
                    "1999-12-31T00:00:00Z",
                    "1999-12-31T01:00:00Z",
                    3_600L
            );
            saveDeletedRecord(
                    deletedRecordStudentId,
                    "2000-01-04T00:00:00Z",
                    "2000-01-04T01:00:00Z",
                    3_600L,
                    "2000-01-05T00:00:00Z"
            );
            saveRecord(mentorId, "2000-01-05T00:00:00Z", "2000-01-05T01:00:00Z", 3_600L);
            saveRecord(
                    endedStudentId,
                    "2000-01-05T00:00:00Z",
                    "2000-01-05T01:00:00Z",
                    3_600L
            );
            saveRecord(
                    otherCohortStudentId,
                    "2000-01-05T00:00:00Z",
                    "2000-01-05T01:00:00Z",
                    3_600L
            );

            return new PageFixture(
                    cohortId,
                    todayAndPeriodStudentId,
                    highPeriodStudentId,
                    periodStudentId,
                    outOfPeriodStudentId,
                    deletedRecordStudentId
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
                    PAGE_FROM,
                    CURRENT_AGGREGATION_DATE,
                    MemberPageQuery.of(
                            "30d",
                            page,
                            size,
                            "periodStudySeconds,desc"
                    )
            );
        }

        private List<MemberSummaryResult> findAll(Long cohortId, String sort) {
            return queryRepository.findActiveStudentStatisticsPage(
                    cohortId,
                    CURRENT_AGGREGATION_DATE,
                    PAGE_FROM,
                    CURRENT_AGGREGATION_DATE,
                    MemberPageQuery.of("30d", 0, 100, sort)
            );
        }

        private List<Long> membershipIds(List<MemberSummaryResult> items) {
            return items.stream()
                    .map(MemberSummaryResult::cohortMembershipId)
                    .toList();
        }

        private MemberSummaryResult emptyMemberSummary(Long membershipId, UUID userId) {
            return new MemberSummaryResult(
                    membershipId,
                    userId,
                    0L,
                    0L,
                    0L,
                    0L,
                    null
            );
        }

        private record PageFixture(
                Long cohortId,
                Long todayAndPeriodStudentId,
                Long highPeriodStudentId,
                Long periodStudentId,
                Long outOfPeriodStudentId,
                Long deletedRecordStudentId
        ) {
        }
    }

    @Nested
    @DisplayName("활성 수강생 수 조회")
    class CountActiveStudents {

        @Test
        @DisplayName("같은 기수의 활성 수강생만 계산 정상 처리")
        void countsOnlyActiveStudentsOfCohort() {
            Long cohortId = insertCohort("수강생 수 대상 기수");
            Long otherCohortId = insertCohort("수강생 수 다른 기수");
            insertActiveStudent(cohortId, 2_201L);
            insertActiveStudent(cohortId, 2_202L);
            insertActiveMentor(cohortId, 2_203L);
            insertEndedStudent(cohortId, 2_204L);
            insertActiveStudent(otherCohortId, 2_205L);

            long result = queryRepository.countActiveStudents(cohortId);

            assertEquals(2L, result);
        }

        @Test
        @DisplayName("활성 수강생이 없으면 0명 정상 처리")
        void returnsZeroWhenActiveStudentDoesNotExist() {
            Long cohortId = insertCohort("빈 기수");

            long result = queryRepository.countActiveStudents(cohortId);

            assertEquals(0L, result);
        }
    }

    @Nested
    @DisplayName("활성 수강생 조회")
    class FindActiveStudent {

        @Test
        @DisplayName("같은 기수의 활성 수강생 조회 정상 처리")
        void returnsActiveStudentOfCohort() {
            Long cohortId = insertCohort("수강생 조회 대상 기수");
            UUID targetUserId = userId(2_301L);
            Long targetStudentId = insertActiveStudent(cohortId, targetUserId);

            MemberReference result = queryRepository.findActiveStudent(
                    cohortId,
                    targetStudentId
            ).orElseThrow();

            assertEquals(new MemberReference(targetStudentId, targetUserId), result);
        }

        @Test
        @DisplayName("역할 상태 또는 기수가 다르면 대상 없음 처리")
        void returnsEmptyWhenMembershipIsNotActiveStudentOfCohort() {
            Long cohortId = insertCohort("수강생 필터 대상 기수");
            Long otherCohortId = insertCohort("수강생 필터 다른 기수");
            Long mentorId = insertActiveMentor(cohortId, 2_401L);
            Long endedStudentId = insertEndedStudent(cohortId, 2_402L);
            Long otherCohortStudentId = insertActiveStudent(otherCohortId, 2_403L);

            Optional<MemberReference> mentor = queryRepository.findActiveStudent(cohortId, mentorId);
            Optional<MemberReference> endedStudent = queryRepository.findActiveStudent(
                    cohortId,
                    endedStudentId
            );
            Optional<MemberReference> otherCohortStudent = queryRepository.findActiveStudent(
                    cohortId,
                    otherCohortStudentId
            );

            assertAll(
                    () -> assertEquals(Optional.empty(), mentor),
                    () -> assertEquals(Optional.empty(), endedStudent),
                    () -> assertEquals(Optional.empty(), otherCohortStudent)
            );
        }
    }

    @Nested
    @DisplayName("기간 요약 조회")
    class SummarizeActiveRecords {

        @Test
        @DisplayName("기간 내 삭제되지 않은 기록만 집계 정상 처리")
        void summarizesOnlyActiveRecordsInPeriod() {
            Long cohortId = insertCohort("기간 요약 대상 기수");
            Long targetStudentId = insertActiveStudent(cohortId, 2_501L);
            savePeriodRecords(targetStudentId);

            PeriodSummary result = queryRepository.summarizeActiveRecords(
                    targetStudentId,
                    PERIOD_FROM,
                    CURRENT_AGGREGATION_DATE
            );

            assertEquals(
                    new PeriodSummary(
                            12_600L,
                            2L,
                            3L,
                            Instant.parse("2000-01-30T02:00:00Z")
                    ),
                    result
            );
        }

        @Test
        @DisplayName("기간 내 기록이 없으면 0초 정상 처리")
        void returnsZerosWhenActiveRecordDoesNotExistInPeriod() {
            Long cohortId = insertCohort("기록 없는 기수");
            Long targetStudentId = insertActiveStudent(cohortId, 2_502L);

            PeriodSummary result = queryRepository.summarizeActiveRecords(
                    targetStudentId,
                    PERIOD_FROM,
                    CURRENT_AGGREGATION_DATE
            );

            assertEquals(new PeriodSummary(0L, 0L, 0L, null), result);
        }
    }

    @Nested
    @DisplayName("일별 학습시간 조회")
    class FindMemberDailyStudySeconds {

        @Test
        @DisplayName("기간 내 삭제되지 않은 기록을 날짜별 집계 정상 처리")
        void aggregatesOnlyActiveRecordsByDate() {
            Long cohortId = insertCohort("일별 집계 대상 기수");
            Long targetStudentId = insertActiveStudent(cohortId, 2_601L);
            savePeriodRecords(targetStudentId);

            List<DailyTotalResult> result = queryRepository.findMemberDailyStudySeconds(
                    targetStudentId,
                    PERIOD_FROM,
                    CURRENT_AGGREGATION_DATE
            );

            assertEquals(
                    List.of(
                            new DailyTotalResult(PERIOD_FROM, 5_400L),
                            new DailyTotalResult(CURRENT_AGGREGATION_DATE, 7_200L)
                    ),
                    result
            );
        }
    }

    @Nested
    @DisplayName("일별 학습 기록 조회")
    class FindMemberDailyRecords {

        @Test
        @DisplayName("선택 집계일의 활성 기록만 안정 정렬 정상 처리")
        void returnsOnlyActiveMemberRecordsOfSelectedAggregationDate() {
            Long cohortId = insertCohort("일별 기록 대상 기수");
            Long targetStudentId = insertActiveStudent(cohortId, 2_701L);
            Long otherStudentId = insertActiveStudent(cohortId, 2_702L);
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
            saveDeletedRecord(
                    targetStudentId,
                    "2000-01-30T04:00:00Z",
                    "2000-01-30T05:00:00Z",
                    3_600L,
                    "2000-01-30T06:00:00Z"
            );
            saveRecord(
                    otherStudentId,
                    "2000-01-30T00:00:00Z",
                    "2000-01-30T01:00:00Z",
                    3_600L
            );
            studyRecordJpaRepository.flush();

            List<MemberDailyRecordResult> records = queryRepository
                    .findMemberDailyRecords(targetStudentId, CURRENT_AGGREGATION_DATE);

            assertEquals(
                    List.of(
                            new MemberDailyRecordResult(
                                    firstRecord.getId(),
                                    Instant.parse("2000-01-30T00:00:00Z"),
                                    Instant.parse("2000-01-30T01:30:00Z"),
                                    5_400L
                            ),
                            new MemberDailyRecordResult(
                                    secondRecord.getId(),
                                    Instant.parse("2000-01-30T02:00:00Z"),
                                    Instant.parse("2000-01-30T03:00:00Z"),
                                    3_600L
                            )
                    ),
                    records
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

    private Long insertActiveStudent(Long cohortId, long userNumber) {
        return insertActiveStudent(cohortId, userId(userNumber));
    }

    private Long insertActiveStudent(Long cohortId, UUID userId) {
        return insertMembership(
                cohortId,
                userId,
                CohortMembershipRole.STUDENT,
                CohortMembershipStatus.ACTIVE
        );
    }

    private Long insertActiveMentor(Long cohortId, long userNumber) {
        return insertMembership(
                cohortId,
                userId(userNumber),
                CohortMembershipRole.MENTOR,
                CohortMembershipStatus.ACTIVE
        );
    }

    private Long insertEndedStudent(Long cohortId, long userNumber) {
        return insertMembership(
                cohortId,
                userId(userNumber),
                CohortMembershipRole.STUDENT,
                CohortMembershipStatus.ENDED
        );
    }

    private Long insertMembership(
            Long cohortId,
            UUID userId,
            CohortMembershipRole role,
            CohortMembershipStatus status
    ) {
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

    private UUID userId(long userNumber) {
        return new UUID(0L, userNumber);
    }

    private void savePeriodRecords(Long cohortMembershipId) {
        saveRecord(
                cohortMembershipId,
                "2000-01-24T00:00:00Z",
                "2000-01-24T01:00:00Z",
                3_600L
        );
        saveRecord(
                cohortMembershipId,
                "2000-01-24T01:00:00Z",
                "2000-01-24T01:30:00Z",
                1_800L
        );
        saveRecord(
                cohortMembershipId,
                "2000-01-30T00:00:00Z",
                "2000-01-30T02:00:00Z",
                7_200L
        );
        saveRecord(
                cohortMembershipId,
                "2000-01-23T00:00:00Z",
                "2000-01-23T01:00:00Z",
                3_600L
        );
        saveDeletedRecord(
                cohortMembershipId,
                "2000-01-25T00:00:00Z",
                "2000-01-25T01:00:00Z",
                3_600L,
                "2000-01-26T00:00:00Z"
        );
        studyRecordJpaRepository.flush();
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
        return studyRecordJpaRepository.save(studyRecord);
    }

    private StudyRecord saveDeletedRecord(
            Long cohortMembershipId,
            String startTime,
            String endTime,
            long studySeconds,
            String deletedAt
    ) {
        StudyRecord studyRecord = saveRecord(
                cohortMembershipId,
                startTime,
                endTime,
                studySeconds
        );
        studyRecord.softDelete(Instant.parse(deletedAt));
        return studyRecord;
    }
}
