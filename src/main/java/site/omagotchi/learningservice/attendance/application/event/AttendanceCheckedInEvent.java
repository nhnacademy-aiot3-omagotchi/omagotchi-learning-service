package site.omagotchi.learningservice.attendance.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 출석 기록의 최초 체크인이 확정되었다.
 */
public record AttendanceCheckedInEvent(
        UUID userId,
        Long cohortId,
        Long attendanceId,
        Instant occurredAt
) {
}
