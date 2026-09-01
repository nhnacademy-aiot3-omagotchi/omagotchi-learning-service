package site.omagotchi.learningservice.attendance.infrastructure;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, QueryDslConfig.class, AttendanceSpacePresenceJpaQuery.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("공간별 현재 체류와 회의 후 복귀 예약 조회")
class AttendanceSpacePresenceJpaQueryIT {

    private static final LocalDate CURRENT_ATTENDANCE_DATE = LocalDate.of(2026, 8, 31);
    private static final UUID ADMIN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000201"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceSpacePresenceJpaQuery query;

    @Test
    @DisplayName("현재 집계일의 미체크아웃 체류와 가장 최근 비회의 공간의 복귀 예약만 집계한다")
    void summarizesOnlyCurrentAttendanceDateAndUncheckedOutPresence() {
        Long cohortId = saveCohort();
        Long labId = saveSpace(cohortId, "체류집계-실습실", "LAB");
        Long meetingRoomId = saveSpace(cohortId, "체류집계-회의실", "MEETING");

        Long currentAttendanceId = saveAttendance(
                saveMembership(cohortId),
                CURRENT_ATTENDANCE_DATE,
                null
        );
        savePresence(currentAttendanceId, "PRESENT", labId,
                "2026-08-31T00:00:00Z", null);

        Long meetingAttendanceId = saveAttendance(
                saveMembership(cohortId),
                CURRENT_ATTENDANCE_DATE,
                null
        );
        savePresence(meetingAttendanceId, "STUDYING", labId,
                "2026-08-31T00:10:00Z", "2026-08-31T01:00:00Z");
        savePresence(meetingAttendanceId, "MEETING", meetingRoomId,
                "2026-08-31T01:00:00Z", null);

        Long checkedOutAttendanceId = saveAttendance(
                saveMembership(cohortId),
                CURRENT_ATTENDANCE_DATE,
                "2026-08-31T02:00:00Z"
        );
        savePresence(checkedOutAttendanceId, "PRESENT", labId,
                "2026-08-31T00:20:00Z", null);

        Long staleAttendanceId = saveAttendance(
                saveMembership(cohortId),
                CURRENT_ATTENDANCE_DATE.minusDays(1),
                null
        );
        savePresence(staleAttendanceId, "PRESENT", labId,
                "2026-08-30T00:00:00Z", null);

        var summaries = query.summarize(
                List.of(labId, meetingRoomId),
                CURRENT_ATTENDANCE_DATE
        );

        assertThat(summaries.get(labId).currentCount()).isEqualTo(1L);
        assertThat(summaries.get(labId).returnReservationCount()).isEqualTo(1L);
        assertThat(summaries.get(meetingRoomId).currentCount()).isEqualTo(1L);
        assertThat(summaries.get(meetingRoomId).returnReservationCount()).isZero();
    }

    private Long saveCohort() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) VALUES (
                            '체류 집계 기수', '설명', '2026-08-01', '2026-12-31', 'ACTIVE', ?
                        ) RETURNING id
                        """,
                Long.class,
                ADMIN_ID
        );
    }

    private Long saveMembership(Long cohortId) {
        UUID userId = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.cohort_memberships (
                            cohort_id, user_id, role, status,
                            requested_at, processed_at, processed_by_user_id
                        ) VALUES (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                cohortId,
                userId,
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                ADMIN_ID
        );
    }

    private Long saveSpace(Long cohortId, String name, String type) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.spaces (
                            cohort_id, name, space_type, capacity, status
                        ) VALUES (?, ?, ?, 20, 'ACTIVE')
                        RETURNING id
                        """,
                Long.class,
                cohortId,
                name,
                type
        );
    }

    private Long saveAttendance(
            Long membershipId,
            LocalDate attendanceDate,
            String checkedOutAt
    ) {
        OffsetDateTime checkedInAt = attendanceDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime checkout = checkedOutAt == null
                ? null
                : OffsetDateTime.parse(checkedOutAt);
        return jdbcTemplate.queryForObject("""
                        INSERT INTO learning_service.attendance_records (
                            cohort_membership_id, attendance_date,
                            checked_in_at, checked_out_at,
                            auto_status, final_status
                        ) VALUES (?, ?, ?, ?, 'PRESENT', 'PRESENT')
                        RETURNING id
                        """,
                Long.class,
                membershipId,
                attendanceDate,
                checkedInAt,
                checkout
        );
    }

    private void savePresence(
            Long attendanceId,
            String state,
            Long spaceId,
            String startedAt,
            String endedAt
    ) {
        jdbcTemplate.update("""
                        INSERT INTO learning_service.presence_intervals (
                            attendance_id, state, space_id, started_at, ended_at
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                attendanceId,
                state,
                spaceId,
                OffsetDateTime.parse(startedAt),
                endedAt == null ? null : OffsetDateTime.parse(endedAt)
        );
    }
}
