package site.omagotchi.learningservice.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "timer_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TimerRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "measured_seconds")
    private Long measuredSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private TimerEndReason endReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TimerRun start(Long cohortMembershipId, Instant startedAt) {
        if (cohortMembershipId == null) {
            throw new IllegalArgumentException("cohortMembershipId가 null입니다.");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt이 null입니다.");
        }

        TimerRun timerRun = new TimerRun();
        timerRun.cohortMembershipId = cohortMembershipId;
        timerRun.startedAt = startedAt;
        return timerRun;
    }

    public boolean isRunning() {
        return endedAt == null && measuredSeconds == null && endReason == null;
    }

    public void stop(Instant endedAt) {
        validateEndTime(endedAt);
        long elapsedSeconds = Duration.between(startedAt, endedAt).getSeconds();

        endTimer(endedAt, elapsedSeconds, TimerEndReason.STOP);
    }

    public void discard(Instant endedAt) {
        validateEndTime(endedAt);
        endTimer(endedAt, null, TimerEndReason.DISCARD);
    }

    public void expire(Instant expiredAt) {
        validateEndTime(expiredAt);
        endTimer(expiredAt, null, TimerEndReason.EXPIRED);
    }

    // ===== Private Methods =====

    private void validateEndTime(Instant endedAt) {
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt이 null입니다.");
        }

        if (!isRunning()) {
            throw new IllegalStateException("종료된 타이머 실행은 변경할 수 없습니다.");
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private void endTimer(
            Instant endedAt,
            Long measuredSeconds,
            TimerEndReason endReason
    ) {
        this.endedAt = endedAt;
        this.measuredSeconds = measuredSeconds;
        this.endReason = endReason;
    }
}
