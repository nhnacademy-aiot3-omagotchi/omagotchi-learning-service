package site.omagotchi.learningservice.attendance.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.AttendanceReminderService;

/** 1분 주기로 현재 발화 창에 들어온 출결 알림을 처리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final AttendanceReminderService reminderService;

    @Scheduled(fixedDelay = 60_000)
    public void sendDue() {
        try {
            reminderService.sendDue();
        } catch (Exception exception) {
            log.error("출결 알림 스케줄 실행에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
