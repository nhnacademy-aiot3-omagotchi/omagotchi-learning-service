package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("일일 미퇴실 마감 통합")
class DailyMissingCheckOutBatchIT {

    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2020, 9, 5);
    private static final Instant CHECKED_IN_AT = Instant.parse("2020-09-05T00:00:00Z");
    /** 예정 종료 18:00 KST + 유예 3시간 = 21:00 KST. */
    private static final OffsetDateTime DEADLINE =
            OffsetDateTime.parse("2020-09-05T12:00:00Z");

    private static TimeZone originalTimeZone;

    /**
     * <b>JVM 기본 시간대를 UTC로 고정한다.</b> {@code hibernate.jdbc.time_zone: UTC}
     * 설정 때문에, 시간대가 없는 {@code TIME} 컬럼인 {@code scheduled_end_time}이
     * JVM 기본 시간대만큼 밀려 읽힌다 — KST 실행기에서는 18:00이 03:00으로 읽혀
     * 마감 시각이 체류 시작보다 앞서고 결과가 달라진다. 고정하지 않으면 같은 코드가
     * 실행기에 따라 다른 값을 만들어 이 검증이 뒤집힌다.
     *
     * <p>고정은 이 테스트를 결정적으로 만들 뿐 원인을 없애지 않는다. 운영 컨테이너의
     * 시간대가 UTC가 아니면 출결 정책 시각(지각·조퇴 판정 포함)이 같은 폭만큼 밀린다.</p>
     */
    @BeforeAll
    static void fixJvmTimeZoneToUtc() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restoreJvmTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Autowired
    private DailyMissingCheckOutBatch batch;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("마감 시각이 지난 출결의 체류와 상태를 마감하고 반복 실행은 무해하다")
    void closesDueAttendanceAndIsIdempotent() {
        Long cohortId = saveCohort();
        savePolicy(cohortId);
        Long membershipId = saveActiveMembership(cohortId);
        Long attendanceId = saveAttendance(membershipId);
        savePresence(attendanceId);

        assertThat(batch.closeDueAttendances(200)).isEqualTo(1);

        assertThat(attendanceStatus(attendanceId)).isEqualTo("MISSING_CHECK_OUT");
        assertThat(checkedOutAt(attendanceId)).isNull();
        assertThat(presenceEndedAt(attendanceId)).isEqualTo(DEADLINE);

        assertThat(batch.closeDueAttendances(200)).isZero();
        assertThat(presenceEndedAt(attendanceId)).isEqualTo(DEADLINE);
    }

    private Long saveCohort() {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) values (?, '설명', '2020-09-01', '2030-09-30', 'ACTIVE', ?)
                        returning id
                        """,
                Long.class,
                "일일-미퇴실-" + UUID.randomUUID(),
                ADMIN_ID
        );
    }

    private void savePolicy(Long cohortId) {
        jdbcTemplate.update("""
                        insert into learning_service.cohort_attendance_policies (
                            cohort_id, timezone, scheduled_start_time, scheduled_end_time,
                            absence_cutoff_time, allowed_away_minutes, updated_by_user_id
                        ) values (?, 'Asia/Seoul', '09:00', '18:00', '10:00', 30, ?)
                        """,
                cohortId,
                ADMIN_ID
        );
    }

    private Long saveActiveMembership(Long cohortId) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohort_memberships (
                            cohort_id, user_id, role, status,
                            requested_at, processed_at, processed_by_user_id
                        ) values (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        returning id
                        """,
                Long.class,
                cohortId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2020-09-01T00:00:00Z"),
                OffsetDateTime.parse("2020-09-01T00:00:00Z"),
                ADMIN_ID
        );
    }

    private Long saveAttendance(Long membershipId) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.attendance_records (
                            cohort_membership_id, attendance_date, checked_in_at,
                            auto_status, final_status
                        ) values (?, ?, ?, 'PRESENT', 'PRESENT')
                        returning id
                        """,
                Long.class,
                membershipId,
                ATTENDANCE_DATE,
                CHECKED_IN_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private void savePresence(Long attendanceId) {
        jdbcTemplate.update("""
                        insert into learning_service.presence_intervals (
                            attendance_id, state, started_at
                        ) values (?, 'PRESENT', ?)
                        """,
                attendanceId,
                CHECKED_IN_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private String attendanceStatus(Long attendanceId) {
        return jdbcTemplate.queryForObject("""
                select auto_status
                  from learning_service.attendance_records
                 where id = ?
                """, String.class, attendanceId);
    }

    private OffsetDateTime checkedOutAt(Long attendanceId) {
        return jdbcTemplate.queryForObject("""
                select checked_out_at
                  from learning_service.attendance_records
                 where id = ?
                """, OffsetDateTime.class, attendanceId);
    }

    private OffsetDateTime presenceEndedAt(Long attendanceId) {
        return jdbcTemplate.queryForObject("""
                select ended_at
                  from learning_service.presence_intervals
                 where attendance_id = ?
                """, OffsetDateTime.class, attendanceId);
    }
}
