package site.omagotchi.learningservice.attendance.application;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("종료 소속 출결 정리 통합")
class EndedMembershipAttendanceCleanupIT {

    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant STARTED_AT = Instant.parse("2026-09-04T00:00:00Z");
    private static final OffsetDateTime ENDED_AT =
            OffsetDateTime.parse("2026-09-04T09:00:00Z");

    @Autowired
    private EndedMembershipAttendanceCleanup cleanup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("여러 날짜의 미퇴실 출결과 체류를 마감하고 반복 실행은 무해하다")
    void closesAllUnresolvedAttendanceAndIsIdempotent() {
        Long membershipId = saveEndedMembership();
        Long attendanceWithPresence = saveAttendance(
                membershipId,
                LocalDate.of(2026, 9, 4),
                STARTED_AT,
                "PRESENT"
        );
        Long attendanceWithoutPresence = saveAttendance(
                membershipId,
                LocalDate.of(2026, 9, 3),
                STARTED_AT.minusSeconds(86_400),
                "PRESENT"
        );
        Long notCheckedIn = savePendingAttendance(
                membershipId,
                LocalDate.of(2026, 9, 2)
        );
        Long alreadyMissing = saveAttendance(
                membershipId,
                LocalDate.of(2026, 9, 1),
                STARTED_AT.minusSeconds(3 * 86_400L),
                "MISSING_CHECK_OUT"
        );
        savePresence(attendanceWithPresence, STARTED_AT);

        assertThat(cleanup.cleanUp(membershipId, ENDED_AT)).isEqualTo(2);

        assertThat(openPresenceCount(attendanceWithPresence)).isZero();
        assertThat(presenceEndedAt(attendanceWithPresence)).isEqualTo(ENDED_AT);
        assertThat(attendanceStatus(attendanceWithPresence)).isEqualTo("MISSING_CHECK_OUT");
        assertThat(attendanceStatus(attendanceWithoutPresence)).isEqualTo("MISSING_CHECK_OUT");
        assertThat(checkedOutAt(attendanceWithPresence)).isNull();
        assertThat(attendanceStatus(notCheckedIn)).isEqualTo("PENDING");
        assertThat(attendanceStatus(alreadyMissing)).isEqualTo("MISSING_CHECK_OUT");

        assertThat(cleanup.cleanUp(membershipId, ENDED_AT.plusMinutes(5))).isZero();
        assertThat(presenceEndedAt(attendanceWithPresence)).isEqualTo(ENDED_AT);
    }

    @Test
    @DisplayName("체류 종료 시각이 잘못된 한 건은 출결 상태도 바꾸지 않는다")
    void keepsAttendanceUnchangedWhenPresenceCloseFails() {
        Long membershipId = saveEndedMembership();
        Long attendanceId = saveAttendance(
                membershipId,
                LocalDate.of(2026, 9, 4),
                STARTED_AT,
                "PRESENT"
        );
        savePresence(attendanceId, STARTED_AT);

        assertThat(cleanup.cleanUp(
                membershipId,
                STARTED_AT.minusSeconds(1).atOffset(ZoneOffset.UTC)
        )).isZero();

        assertThat(openPresenceCount(attendanceId)).isEqualTo(1);
        assertThat(attendanceStatus(attendanceId)).isEqualTo("PRESENT");
    }

    private Long saveEndedMembership() {
        Long cohortId = jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) values (?, '설명', '2026-09-01', '2026-09-30', 'ACTIVE', ?)
                        returning id
                        """,
                Long.class,
                "출결정리-" + UUID.randomUUID(),
                ADMIN_ID
        );
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohort_memberships (
                            cohort_id, user_id, role, status,
                            requested_at, processed_at, processed_by_user_id, ended_at
                        ) values (?, ?, 'STUDENT', 'ENDED', ?, ?, ?, ?)
                        returning id
                        """,
                Long.class,
                cohortId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                ADMIN_ID,
                ENDED_AT
        );
    }

    private Long saveAttendance(
            Long membershipId,
            LocalDate date,
            Instant checkedInAt,
            String status
    ) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.attendance_records (
                            cohort_membership_id, attendance_date, checked_in_at,
                            auto_status, final_status
                        ) values (?, ?, ?, ?, ?)
                        returning id
                        """,
                Long.class,
                membershipId,
                date,
                checkedInAt.atOffset(ZoneOffset.UTC),
                status,
                status
        );
    }

    private Long savePendingAttendance(Long membershipId, LocalDate date) {
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.attendance_records (
                            cohort_membership_id, attendance_date, auto_status, final_status
                        ) values (?, ?, 'PENDING', 'PENDING')
                        returning id
                        """,
                Long.class,
                membershipId,
                date
        );
    }

    private void savePresence(Long attendanceId, Instant startedAt) {
        jdbcTemplate.update("""
                        insert into learning_service.presence_intervals (
                            attendance_id, state, started_at
                        ) values (?, 'PRESENT', ?)
                        """,
                attendanceId,
                startedAt.atOffset(ZoneOffset.UTC)
        );
    }

    private int openPresenceCount(Long attendanceId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                  from learning_service.presence_intervals
                 where attendance_id = ? and ended_at is null
                """, Integer.class, attendanceId);
        return count == null ? 0 : count;
    }

    private OffsetDateTime presenceEndedAt(Long attendanceId) {
        return jdbcTemplate.queryForObject("""
                select ended_at
                  from learning_service.presence_intervals
                 where attendance_id = ?
                """, OffsetDateTime.class, attendanceId);
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
}
