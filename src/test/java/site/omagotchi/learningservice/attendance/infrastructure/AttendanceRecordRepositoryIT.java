package site.omagotchi.learningservice.attendance.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, QueryDslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("출결 날짜·페이지 저장소")
class AttendanceRecordRepositoryIT {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Test
    @DisplayName("내 출결을 inclusive 날짜 범위와 날짜 내림차순으로 페이지 조회한다")
    void pagesMyAttendanceByInclusiveDateRange() {
        Long membershipId = saveMembership();
        saveAttendance(membershipId, LocalDate.of(2026, 8, 18));
        saveAttendance(membershipId, LocalDate.of(2026, 8, 19));
        saveAttendance(membershipId, LocalDate.of(2026, 8, 20));
        var pageable = PageRequest.of(
                0,
                1,
                Sort.by(Sort.Order.desc("attendanceDate"), Sort.Order.desc("id"))
        );

        var result = attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDateBetween(
                membershipId,
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 20),
                pageable
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).extracting(record -> record.getAttendanceDate())
                .containsExactly(LocalDate.of(2026, 8, 20));
    }

    private Long saveMembership() {
        Long cohortId = jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) values ('출결 페이지 기수', '설명', '2026-08-01', '2026-08-31', 'ACTIVE', ?)
                        returning id
                        """, Long.class, ADMIN_ID);
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohort_memberships (
                            cohort_id, user_id, role, status, requested_at, processed_at, processed_by_user_id
                        ) values (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        returning id
                        """,
                Long.class,
                cohortId,
                USER_ID,
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                ADMIN_ID
        );
    }

    private void saveAttendance(Long membershipId, LocalDate date) {
        jdbcTemplate.update("""
                        insert into learning_service.attendance_records (
                            cohort_membership_id, attendance_date, auto_status, final_status
                        ) values (?, ?, 'PRESENT', 'PRESENT')
                        """,
                membershipId,
                date
        );
    }
}
