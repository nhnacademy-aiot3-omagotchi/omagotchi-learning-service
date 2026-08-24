package site.omagotchi.learningservice.prediction.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.gamification.application.GamificationErrorCode;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        PredictionFeatureQueryAdapter.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("예측 피처 원천 데이터 조회")
class PredictionFeatureQueryAdapterIT {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final UUID MANAGER_ID = new UUID(0L, 2L);
    private static final LocalDate FEATURE_DATE = LocalDate.parse("2000-01-30");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PredictionFeatureQueryAdapter queryAdapter;

    @Test
    @DisplayName("확정 학습 기록과 최종 출결 및 게임 원천 데이터 조회 정상 처리")
    void readsConfirmedStudyFinalAttendanceAndGamificationData() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);
        insertStudyRecords(membershipId);
        insertAttendanceRecords(membershipId);
        insertGamificationData();

        PredictionFeatureSnapshot snapshot = queryAdapter.read(
                USER_ID,
                cohortId,
                membershipId,
                FEATURE_DATE
        );

        assertAll(
                () -> assertEquals(FEATURE_DATE, snapshot.featureDate()),
                () -> assertEquals(LocalDate.parse("2000-01-05"), snapshot.membershipStartDate()),
                () -> assertEquals("Asia/Seoul", snapshot.attendanceTimezone()),
                () -> assertEquals(LocalDate.parse("2000-01-10"), snapshot.study().firstStudyDate()),
                () -> assertEquals(10_800L, snapshot.study().totalStudySeconds()),
                () -> assertEquals(2, snapshot.study().recentDailyStudySeconds().size()),
                () -> assertEquals(
                        3_600L,
                        snapshot.study().recentDailyStudySeconds().getFirst().studySeconds()
                ),
                () -> assertEquals(
                        7_200L,
                        snapshot.study().recentDailyStudySeconds().getLast().studySeconds()
                ),
                () -> assertEquals(1L, snapshot.study().studiedWeekdaysAll()),
                () -> assertEquals(6, snapshot.attendance().recentAttendance().size()),
                () -> assertEquals(0L, snapshot.attendance().lateStudiedDaysAll()),
                () -> assertEquals(
                        AttendanceStatus.PRESENT,
                        snapshot.attendance().recentAttendance().getLast().finalStatus()
                ),
                () -> assertEquals(8, snapshot.gamification().representativeLevel()),
                () -> assertEquals(15L, snapshot.gamification().completedQuestsTotal()),
                () -> assertEquals(4, snapshot.gamification().dailyQuestSummaries().size()),
                () -> assertEquals(
                        5L,
                        snapshot.gamification().dailyQuestSummaries().get(2).generatedCount()
                ),
                () -> assertEquals(
                        5L,
                        snapshot.gamification().dailyQuestSummaries().get(2).completedCount()
                )
        );
    }

    @Test
    @DisplayName("기록이 없으면 빈 원천 내역과 0 누적값 조회")
    void readsColdStartAsEmptyHistoriesAndZeroTotals() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);
        insertRepresentativeCharacter();

        PredictionFeatureSnapshot snapshot = queryAdapter.read(
                USER_ID,
                cohortId,
                membershipId,
                FEATURE_DATE
        );

        assertAll(
                () -> assertNull(snapshot.study().firstStudyDate()),
                () -> assertEquals(0L, snapshot.study().totalStudySeconds()),
                () -> assertEquals(0, snapshot.study().recentDailyStudySeconds().size()),
                () -> assertEquals(0L, snapshot.study().studiedWeekdaysAll()),
                () -> assertEquals(0, snapshot.attendance().recentAttendance().size()),
                () -> assertEquals(0L, snapshot.attendance().lateStudiedDaysAll()),
                () -> assertEquals(8, snapshot.gamification().representativeLevel()),
                () -> assertEquals(0L, snapshot.gamification().completedQuestsTotal()),
                () -> assertEquals(0, snapshot.gamification().dailyQuestSummaries().size())
        );
    }

    @Test
    @DisplayName("대표 캐릭터가 없으면 기존 대표 캐릭터 없음 예외")
    void rejectsPredictionWhenRepresentativeCharacterDoesNotExist() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> queryAdapter.read(
                        USER_ID,
                        cohortId,
                        membershipId,
                        FEATURE_DATE
                )
        );

        assertSame(GamificationErrorCode.REPRESENTATIVE_CHARACTER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("같은 날 확정 StudyRecord가 여러 개여도 학습일과 지각일을 한 번만 집계")
    void countsDistinctStudiedAndLateDays() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);
        insertRepresentativeCharacter();
        LocalDate featureDate = LocalDate.parse("2000-01-31");
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000041",
                featureDate.toString(),
                "2000-01-31T00:00:00Z",
                "2000-01-31T01:00:00Z",
                3_600L,
                null
        );
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000042",
                featureDate.toString(),
                "2000-01-31T02:00:00Z",
                "2000-01-31T03:00:00Z",
                3_600L,
                null
        );
        insertAttendance(membershipId, "2000-01-28", "LATE", "2000-01-28T00:30:00Z");
        insertAttendance(membershipId, featureDate.toString(), "LATE", "2000-01-31T00:30:00Z");

        PredictionFeatureSnapshot snapshot = queryAdapter.read(
                USER_ID,
                cohortId,
                membershipId,
                featureDate
        );

        assertAll(
                () -> assertEquals(1, snapshot.study().recentDailyStudySeconds().size()),
                () -> assertEquals(
                        7_200L,
                        snapshot.study().recentDailyStudySeconds().getFirst().studySeconds()
                ),
                () -> assertEquals(1L, snapshot.study().studiedWeekdaysAll()),
                () -> assertEquals(1L, snapshot.attendance().lateStudiedDaysAll())
        );
    }

    @Test
    @DisplayName("PENDING 출결도 예측용 최근 출결 메타데이터로 조회")
    void readsPendingAttendanceAsRecentMetadata() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);
        insertRepresentativeCharacter();
        LocalDate featureDate = LocalDate.parse("2000-03-15");
        insertAttendance(membershipId, featureDate.toString(), "PENDING", "2000-03-15T00:00:00Z");

        PredictionFeatureSnapshot snapshot = queryAdapter.read(
                USER_ID,
                cohortId,
                membershipId,
                featureDate
        );

        assertAll(
                () -> assertEquals(1, snapshot.attendance().recentAttendance().size()),
                () -> assertSame(
                        AttendanceStatus.PENDING,
                        snapshot.attendance().recentAttendance().getFirst().finalStatus()
                )
        );
    }

    @Test
    @DisplayName("일일 퀘스트가 부분 생성되어도 실제 생성 및 완료 수 조회")
    void readsPartiallyGeneratedDailyQuestCounts() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long membershipId = insertMembership(cohortId);
        insertRepresentativeCharacter();
        insertDailyQuest("2000-01-24", 1, "COMPLETED", "2000-01-24T09:00:00Z", null);

        PredictionFeatureSnapshot snapshot = queryAdapter.read(
                USER_ID,
                cohortId,
                membershipId,
                FEATURE_DATE
        );

        assertAll(
                () -> assertEquals(1, snapshot.gamification().dailyQuestSummaries().size()),
                () -> assertEquals(
                        1L,
                        snapshot.gamification().dailyQuestSummaries().getFirst().generatedCount()
                ),
                () -> assertEquals(
                        1L,
                        snapshot.gamification().dailyQuestSummaries().getFirst().completedCount()
                )
        );
    }

    @Test
    @DisplayName("소속 정보가 없으면 기수 소속 없음 예외")
    void rejectsPredictionWhenMembershipDoesNotExist() {
        Long cohortId = insertCohort();
        insertAttendancePolicy(cohortId);
        Long nonExistentMembershipId = 999_999L;

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> queryAdapter.read(
                        USER_ID,
                        cohortId,
                        nonExistentMembershipId,
                        FEATURE_DATE
                )
        );

        assertSame(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("출결 정책이 없으면 기존 출결 정책 없음 예외")
    void rejectsPredictionWhenAttendancePolicyDoesNotExist() {
        Long cohortId = insertCohort();
        Long membershipId = insertMembership(cohortId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> queryAdapter.read(
                        USER_ID,
                        cohortId,
                        membershipId,
                        FEATURE_DATE
                )
        );

        assertSame(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND, exception.getErrorCode());
    }

    private Long insertCohort() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohorts (
                    name,
                    start_date,
                    end_date,
                    status,
                    created_by_user_id
                ) VALUES ('예측 테스트 기수', DATE '2000-01-01', DATE '2000-12-31', 'ACTIVE', ?)
                RETURNING id
                """, Long.class, MANAGER_ID);
    }

    private void insertAttendancePolicy(Long cohortId) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.cohort_attendance_policies (
                    cohort_id,
                    timezone,
                    scheduled_start_time,
                    scheduled_end_time,
                    absence_cutoff_time,
                    allowed_away_minutes,
                    updated_by_user_id
                ) VALUES (?, 'Asia/Seoul', TIME '09:00', TIME '18:00', TIME '10:00', 30, ?)
                """, cohortId, MANAGER_ID);
    }

    private Long insertMembership(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO learning_service.cohort_memberships (
                    cohort_id,
                    user_id,
                    role,
                    status,
                    requested_at,
                    processed_at,
                    processed_by_user_id
                ) VALUES (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                cohortId,
                USER_ID,
                OffsetDateTime.parse("2000-01-03T00:00:00Z"),
                OffsetDateTime.parse("2000-01-04T15:00:00Z"),
                MANAGER_ID
        );
    }

    private void insertStudyRecords(Long membershipId) {
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000010",
                "2000-01-10",
                "2000-01-10T00:00:00Z",
                "2000-01-10T01:00:00Z",
                3_600L,
                null
        );
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000030",
                "2000-01-30",
                "2000-01-30T00:00:00Z",
                "2000-01-30T02:00:00Z",
                7_200L,
                null
        );
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000029",
                "2000-01-29",
                "2000-01-29T00:00:00Z",
                "2000-01-29T01:00:00Z",
                3_600L,
                OffsetDateTime.parse("2000-01-29T02:00:00Z")
        );
        insertStudyRecord(
                membershipId,
                "00000000-0000-0000-0000-000000000031",
                "2000-01-31",
                "2000-01-31T00:00:00Z",
                "2000-01-31T01:00:00Z",
                3_600L,
                null
        );
    }

    private void insertStudyRecord(
            Long membershipId,
            String id,
            String aggregationDate,
            String startedAt,
            String endedAt,
            long studySeconds,
            OffsetDateTime deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.study_records (
                    id,
                    cohort_membership_id,
                    aggregation_date,
                    start_time,
                    end_time,
                    study_seconds,
                    deleted_at
                ) VALUES (?::uuid, ?, ?::date, ?::timestamptz, ?::timestamptz, ?, ?)
                """,
                id,
                membershipId,
                aggregationDate,
                startedAt,
                endedAt,
                studySeconds,
                deletedAt
        );
    }

    private void insertAttendanceRecords(Long membershipId) {
        insertAttendance(membershipId, "2000-01-04", "PRESENT", "2000-01-04T00:00:00Z");
        insertAttendance(membershipId, "2000-01-05", "LEFT_EARLY", "2000-01-05T00:00:00Z");
        insertAttendance(membershipId, "2000-01-06", "LATE", "2000-01-06T00:10:00Z");
        insertAttendance(membershipId, "2000-01-07", "MISSING_CHECK_OUT", "2000-01-07T00:00:00Z");
        insertAttendance(membershipId, "2000-01-09", "PRESENT", "2000-01-09T00:00:00Z");
        insertAttendance(membershipId, "2000-01-08", "ABSENT", null);
        insertAttendance(membershipId, "2000-01-10", "PRESENT", null);
    }

    private void insertAttendance(
            Long membershipId,
            String attendanceDate,
            String finalStatus,
            String checkedInAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.attendance_records (
                    cohort_membership_id,
                    attendance_date,
                    checked_in_at,
                    auto_status,
                    final_status
                ) VALUES (?, ?::date, ?::timestamptz, ?, ?)
                """,
                membershipId,
                attendanceDate,
                checkedInAt,
                finalStatus,
                finalStatus
        );
    }

    private void insertGamificationData() {
        insertRepresentativeCharacter();

        insertDailyQuestDay("2000-01-24", 5, "COMPLETED");
        insertDailyQuestDay("2000-01-25", 5, "CLAIMED");
        // EXPIRED여도 completedAt이 남아 있으면 완료 사실로 집계한다.
        insertDailyQuestDay("2000-01-26", 5, "EXPIRED");
        insertDailyQuestDay("2000-01-27", 0, "EXPIRED");
        // 기준일 이후 데이터는 조회에서 제외한다.
        insertDailyQuestDay("2000-01-31", 5, "COMPLETED");
    }

    private void insertRepresentativeCharacter() {
        Long gameCharacterId = jdbcTemplate.queryForObject(
                "SELECT id FROM learning_service.game_characters ORDER BY id LIMIT 1",
                Long.class
        );
        jdbcTemplate.update("""
                INSERT INTO learning_service.user_characters (
                    user_id,
                    game_character_id,
                    nickname,
                    is_representative,
                    total_xp,
                    level,
                    advancement_stage,
                    color_id
                ) VALUES (?, ?, '예측테스트', TRUE, 700, 8, 'BASE', 'original')
                """, USER_ID, gameCharacterId);
    }

    private void insertDailyQuestDay(String questDate, int completedCount, String status) {
        for (int sequence = 1; sequence <= 5; sequence++) {
            boolean completed = sequence <= completedCount;
            insertDailyQuest(
                    questDate,
                    sequence,
                    status,
                    completed ? questDate + "T09:00:00Z" : null,
                    completed && "CLAIMED".equals(status) ? questDate + "T10:00:00Z" : null
            );
        }
    }

    private void insertDailyQuest(
            String questDate,
            int codeSequence,
            String status,
            String completedAt,
            String claimedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.user_daily_quests (
                    user_id,
                    quest_date,
                    type,
                    code,
                    title,
                    target_count,
                    progress_count,
                    reward_xp,
                    status,
                    completed_at,
                    claimed_at
                ) VALUES (?, ?::date, 'ROUTINE', ?, '예측 테스트', 1, 1, 10,
                          ?, ?::timestamptz, ?::timestamptz)
                """,
                USER_ID,
                questDate,
                "PREDICTION_TEST_" + codeSequence,
                status,
                completedAt,
                claimedAt
        );
    }
}
