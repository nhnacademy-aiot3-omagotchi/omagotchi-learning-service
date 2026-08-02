package site.omagotchi.learningservice.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 출결 변경 로그 entity
 */

@Entity
@Table(name = "attendance_change_logs", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_id", nullable = false)
    private Long attendanceId;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 20)
    private AttendanceStatus nextStatus;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    public static AttendanceChangeLog create(
            Long attendanceId,
            UUID actorUserId,
            AttendanceStatus previousStatus,
            AttendanceStatus nextStatus,
            String reason,
            String requestId
    ) {
        AttendanceChangeLog log = new AttendanceChangeLog();
        log.attendanceId = attendanceId;
        log.actorUserId = actorUserId;
        log.previousStatus = previousStatus;
        log.nextStatus = nextStatus;
        log.reason = truncate(reason, 500);
        log.requestId = truncate(requestId, 100);
        log.changedAt = OffsetDateTime.now();
        return log;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
