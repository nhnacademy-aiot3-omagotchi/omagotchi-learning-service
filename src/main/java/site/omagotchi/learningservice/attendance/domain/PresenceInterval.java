package site.omagotchi.learningservice.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 출결 기록에 속한 체류 구간 entity
 */
@Entity
@Table(name = "presence_intervals", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PresenceInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attendance_id", nullable = false)
    private Long attendanceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PresenceState state;

    @Column(name = "space_id")
    private Long spaceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PresenceInterval start(
            Long attendanceId,
            PresenceState state,
            Long spaceId,
            Instant startedAt
    ) {
        PresenceInterval interval = new PresenceInterval();
        interval.attendanceId = attendanceId;
        interval.state = state;
        interval.spaceId = spaceId;
        interval.startedAt = startedAt;
        return interval;
    }

    public void end(Instant endedAt) {
        if (this.endedAt != null) {
            return;
        }
        this.endedAt = endedAt;
    }
}
