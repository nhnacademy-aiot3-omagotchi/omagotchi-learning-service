package site.omagotchi.learningservice.attendance.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;
import site.omagotchi.learningservice.global.config.QueryDslConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        AttendanceReminderJpaPersistence.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("출결 알림 이력 저장소")
class AttendanceReminderRepositoryIT {

    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 5);
    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-05T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceReminderRepository reminderRepository;

    @Autowired
    private AttendanceReminderJpaPersistence reminderPersistence;

    @Test
    @DisplayName("SENT와 제한 시간 안의 PENDING만 중복 발송 차단 대상으로 조회한다")
    void findsOnlyBlockingMembershipIds() {
        Long sentMembershipId = saveMembership();
        AttendanceReminder sent = reminder(sentMembershipId, NOW.minusMinutes(5));
        sent.markSent(1, NOW.minusMinutes(4));
        reminderRepository.save(sent);

        Long pendingMembershipId = saveMembership();
        reminderRepository.save(reminder(pendingMembershipId, NOW.minusSeconds(30)));

        Long failedMembershipId = saveMembership();
        AttendanceReminder failed = reminder(failedMembershipId, NOW.minusMinutes(2));
        failed.markFailed(1, "telegram unavailable", NOW.minusMinutes(1));
        reminderRepository.save(failed);

        Long skippedMembershipId = saveMembership();
        AttendanceReminder skipped = reminder(skippedMembershipId, NOW.minusMinutes(2));
        skipped.markSkipped(1, "notifications disabled", NOW.minusMinutes(1));
        reminderRepository.save(skipped);

        Long stalePendingMembershipId = saveMembership();
        reminderRepository.save(reminder(stalePendingMembershipId, NOW.minusMinutes(2)));
        reminderRepository.flush();

        List<Long> result = reminderRepository.findBlockingMembershipIds(
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                List.of(
                        sentMembershipId,
                        pendingMembershipId,
                        failedMembershipId,
                        skippedMembershipId,
                        stalePendingMembershipId
                ),
                NOW.minusMinutes(1)
        );

        assertThat(result).containsExactlyInAnyOrder(sentMembershipId, pendingMembershipId);
    }

    @Test
    @DisplayName("같은 소속·날짜·유형·채널의 이력은 유니크 제약으로 하나만 허용한다")
    void rejectsDuplicateReminder() {
        Long membershipId = saveMembership();
        reminderRepository.saveAndFlush(reminder(membershipId));

        assertThatThrownBy(() -> reminderRepository.saveAndFlush(reminder(membershipId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PENDING 이력 선점은 같은 발송 키에서 최초 한 번만 성공한다")
    void insertsPendingReminderOnlyOnce() {
        Long membershipId = saveMembership();

        assertThat(reminderPersistence.insertIfAbsent(reminder(membershipId))).isTrue();
        assertThat(reminderPersistence.insertIfAbsent(reminder(membershipId))).isFalse();
    }

    private AttendanceReminder reminder(Long membershipId) {
        return reminder(membershipId, NOW);
    }

    private AttendanceReminder reminder(Long membershipId, OffsetDateTime startedAt) {
        return AttendanceReminder.pending(
                membershipId,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                startedAt
        );
    }

    private Long saveMembership() {
        Long cohortId = jdbcTemplate.queryForObject("""
                        insert into learning_service.cohorts (
                            name, description, start_date, end_date, status, created_by_user_id
                        ) values ('출결 알림 기수', '설명', '2026-09-01', '2026-09-30', 'ACTIVE', ?)
                        returning id
                        """, Long.class, ADMIN_ID);
        return jdbcTemplate.queryForObject("""
                        insert into learning_service.cohort_memberships (
                            cohort_id, user_id, role, status, requested_at, processed_at,
                            processed_by_user_id
                        ) values (?, ?, 'STUDENT', 'ACTIVE', ?, ?, ?)
                        returning id
                        """,
                Long.class,
                cohortId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                ADMIN_ID
        );
    }
}
