package site.omagotchi.learningservice.attendance.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("출결 알림 JPA 저장 경계")
class AttendanceReminderJpaPersistenceTest {

    @Mock
    private AttendanceReminderRepository repository;

    @InjectMocks
    private AttendanceReminderJpaPersistence persistence;

    @Test
    @DisplayName("PENDING 이력이 삽입되면 true를 반환한다")
    void returnsTrueWhenPendingHistoryIsInserted() {
        AttendanceReminder reminder = reminder();
        given(repository.insertPendingIfAbsent(
                reminder.getCohortMembershipId(),
                reminder.getAttendanceDate(),
                reminder.getReminderType().name(),
                reminder.getChannel().name(),
                reminder.getAttemptCount(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        )).willReturn(1);

        assertThat(persistence.insertIfAbsent(reminder)).isTrue();
    }

    @Test
    @DisplayName("동일 자연 키의 이력이 이미 있으면 false를 반환한다")
    void returnsFalseWhenHistoryAlreadyExists() {
        given(repository.insertPendingIfAbsent(
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(0);

        assertThat(persistence.insertIfAbsent(reminder())).isFalse();
    }

    private AttendanceReminder reminder() {
        return AttendanceReminder.pending(
                10L,
                LocalDate.of(2026, 9, 5),
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                OffsetDateTime.parse("2026-09-05T00:00:00Z")
        );
    }
}
