package site.omagotchi.learningservice.attendance.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;

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
    @DisplayName("이력을 flush까지 저장하면 true를 반환한다")
    void returnsTrueWhenHistoryIsSaved() {
        AttendanceReminder reminder = reminder();
        given(repository.saveAndFlush(reminder)).willReturn(reminder);

        assertThat(persistence.saveIfAbsent(reminder)).isTrue();
    }

    @Test
    @DisplayName("동일 이력의 유니크 제약 위반은 false로 반환한다")
    void returnsFalseOnDuplicateHistory() {
        given(repository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(persistence.saveIfAbsent(reminder())).isFalse();
    }

    private AttendanceReminder reminder() {
        return AttendanceReminder.sent(
                10L,
                LocalDate.of(2026, 9, 5),
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        );
    }
}
