package site.omagotchi.learningservice.attendance.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.AttendanceReminderService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("출결 알림 스케줄러")
class AttendanceReminderSchedulerTest {

    @Mock
    private AttendanceReminderService reminderService;

    @InjectMocks
    private AttendanceReminderScheduler scheduler;

    @Test
    @DisplayName("현재 발화 대상 처리를 서비스에 위임한다")
    void delegatesDueReminders() {
        scheduler.sendDue();

        verify(reminderService).sendDue();
    }

    @Test
    @DisplayName("한 주기의 실패를 삼켜 다음 주기 실행을 보존한다")
    void isolatesIterationFailure() {
        willThrow(new IllegalStateException("database unavailable"))
                .given(reminderService).sendDue();

        assertThatCode(scheduler::sendDue).doesNotThrowAnyException();
    }
}
