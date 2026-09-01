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
import java.util.Objects;

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
        if (attendanceId == null || attendanceId <= 0L) {
            throw new IllegalArgumentException("출결 기록 ID는 양수여야 합니다.");
        }
        PresenceInterval interval = new PresenceInterval();
        interval.attendanceId = attendanceId;
        interval.state = Objects.requireNonNull(state, "체류 상태는 필수입니다.");
        interval.spaceId = spaceId;
        interval.startedAt = Objects.requireNonNull(startedAt, "체류 시작 시각은 필수입니다.");
        return interval;
    }

    public void end(Instant endedAt) {
        if (this.endedAt != null) {
            return;
        }
        Instant requiredEndedAt = Objects.requireNonNull(
                endedAt,
                "체류 종료 시각은 필수입니다."
        );
        if (requiredEndedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "체류 종료 시각은 시작 시각보다 빠를 수 없습니다."
            );
        }
        this.endedAt = requiredEndedAt;
    }
}
