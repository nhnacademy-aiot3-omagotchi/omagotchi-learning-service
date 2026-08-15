package site.omagotchi.learningservice.ranking.infrastructure.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
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
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.RankedStudyMember;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.StudyRankingRows;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        StudyRankingQueryDslRepository.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("QueryDSL 학습 랭킹 조회")
class StudyRankingQueryDslRepositoryIT {

    private static final UUID CREATED_BY_USER_ID = new UUID(0L, 1L);
    private static final OffsetDateTime PROCESSED_AT = OffsetDateTime.parse(
            "2000-01-01T00:00:00Z"
    );
    private static final StudyRankingWindow WINDOW = new StudyRankingWindow(
            LocalDate.parse("2000-01-03"),
            LocalDate.parse("2000-01-07")
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudyRankingQueryDslRepository repository;

    private long recordNumber;

    @BeforeEach
    void resetRecordNumber() {
        recordNumber = 100L;
    }

    @Nested
    @DisplayName("보드와 포커스 회원 조회")
    class FindBoardAndMember {

        @Test
        @DisplayName("동점 경계와 랭킹 밖 내 순위 정상 처리")
        void returnsAllTiesAndFocusedMemberFromOneRankingSet() {
            RankingFixture fixture = createFixture();

            StudyRankingRows rows = repository.findBoardAndMember(
                    WINDOW,
                    2,
                    fixture.cohortId(),
                    fixture.focusedMembershipId()
            );

            assertAll(
                    () -> assertEquals(4L, rows.rankedMemberCount()),
                    () -> assertEquals(3, rows.leaders().size()),
                    () -> assertEquals(
                            List.of(
                                    fixture.firstMembershipId(),
                                    fixture.firstTieMembershipId(),
                                    fixture.secondTieMembershipId()
                            ),
                            rows.leaders().stream()
                                    .map(RankedStudyMember::cohortMembershipId)
                                    .toList()
                    ),
                    () -> assertEquals(
                            List.of(1L, 2L, 2L),
                            rows.leaders().stream()
                                    .map(RankedStudyMember::rank)
                                    .toList()
                    ),
                    () -> assertEquals(
                            4L,
                            rows.focusedMember().orElseThrow().rank()
                    ),
                    () -> assertEquals(
                            1_800L,
                            rows.focusedMember().orElseThrow().studySeconds()
                    )
            );
        }

        @Test
        @DisplayName("요청 순위보다 인원이 적으면 전체 인원 정상 처리")
        void returnsAllMembersWhenMaxRankExceedsPopulation() {
            RankingFixture fixture = createFixture();

            StudyRankingRows rows = repository.findBoard(
                    WINDOW,
                    100,
                    fixture.cohortId()
            );

            assertAll(
                    () -> assertEquals(4L, rows.rankedMemberCount()),
                    () -> assertEquals(4, rows.leaders().size()),
                    () -> assertTrue(rows.focusedMember().isEmpty())
            );
        }
    }

    @Nested
    @DisplayName("내 순위 조회")
    class FindMember {

        @Test
        @DisplayName("진행 중 타이머만 있으면 미랭크 정상 처리")
        void excludesActiveTimerRun() {
            RankingFixture fixture = createFixture();
            insertActiveTimerRun(fixture.timerOnlyMembershipId());

            StudyRankingRows rows = repository.findMember(
                    WINDOW,
                    fixture.cohortId(),
                    fixture.timerOnlyMembershipId()
            );

            assertAll(
                    () -> assertEquals(4L, rows.rankedMemberCount()),
                    () -> assertTrue(rows.leaders().isEmpty()),
                    () -> assertTrue(rows.focusedMember().isEmpty())
            );
        }
    }

    @Test
    @DisplayName("스냅샷 테이블 제거 마이그레이션 정상 처리")
    void dropsSnapshotTables() {
        String entriesTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('learning_service.ranking_snapshot_entries')::TEXT",
                String.class
        );
        String snapshotsTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('learning_service.ranking_snapshots')::TEXT",
                String.class
        );

        assertAll(
                () -> assertNull(entriesTable),
                () -> assertNull(snapshotsTable)
        );
    }

    private RankingFixture createFixture() {
        Long cohortId = insertCohort("랭킹 대상 기수");
        Long otherCohortId = insertCohort("다른 기수");
        Long firstMembershipId = insertMembership(cohortId, 10L, "STUDENT", "ACTIVE");
        Long firstTieMembershipId = insertMembership(
                cohortId,
                11L,
                "STUDENT",
                "ACTIVE"
        );
        Long secondTieMembershipId = insertMembership(
                cohortId,
                12L,
                "STUDENT",
                "ACTIVE"
        );
        Long focusedMembershipId = insertMembership(
                cohortId,
                13L,
                "STUDENT",
                "ACTIVE"
        );
        Long timerOnlyMembershipId = insertMembership(
                cohortId,
                14L,
                "STUDENT",
                "ACTIVE"
        );
        Long mentorMembershipId = insertMembership(cohortId, 15L, "MENTOR", "ACTIVE");
        Long endedMembershipId = insertMembership(cohortId, 16L, "STUDENT", "ENDED");
        Long otherCohortMembershipId = insertMembership(
                otherCohortId,
                17L,
                "STUDENT",
                "ACTIVE"
        );

        insertStudyRecord(firstMembershipId, "2000-01-04", 7_200L, false);
        insertStudyRecord(firstTieMembershipId, "2000-01-05", 3_600L, false);
        insertStudyRecord(secondTieMembershipId, "2000-01-06", 3_600L, false);
        insertStudyRecord(focusedMembershipId, "2000-01-07", 1_800L, false);
        insertStudyRecord(focusedMembershipId, "2000-01-02", 9_000L, false);
        insertStudyRecord(focusedMembershipId, "2000-01-05", 9_000L, true);
        insertStudyRecord(mentorMembershipId, "2000-01-05", 10_000L, false);
        insertStudyRecord(endedMembershipId, "2000-01-05", 10_000L, false);
        insertStudyRecord(otherCohortMembershipId, "2000-01-05", 10_000L, false);

        return new RankingFixture(
                cohortId,
                firstMembershipId,
                firstTieMembershipId,
                secondTieMembershipId,
                focusedMembershipId,
                timerOnlyMembershipId
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
                """, Long.class, name, CREATED_BY_USER_ID);
    }

    private Long insertMembership(
            Long cohortId,
            long userNumber,
            String role,
            String status
    ) {
        OffsetDateTime endedAt = "ENDED".equals(status)
                ? PROCESSED_AT.plusDays(10)
                : null;
        UUID userId = new UUID(0L, userNumber);
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

    private record RankingFixture(
            Long cohortId,
            Long firstMembershipId,
            Long firstTieMembershipId,
            Long secondTieMembershipId,
            Long focusedMembershipId,
            Long timerOnlyMembershipId
    ) {
    }
}
