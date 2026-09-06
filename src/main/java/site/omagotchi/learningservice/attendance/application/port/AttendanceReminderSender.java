package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** 출결 알림을 외부 채널로 보내는 경계. */
public interface AttendanceReminderSender {

    /** 발송을 시작하고 Telegram 응답을 기다리지 않고 반환한다. */
    CompletionStage<Boolean> sendAsync(Reminder reminder);

    record Reminder(
            UUID recipientUserId,
            String cohortName,
            ReminderType type,
            OffsetDateTime scheduledAt
    ) {
    }
}
