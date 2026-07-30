package site.omagotchi.learningservice.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
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
        TimerRun timerRun = new TimerRun();
        timerRun.cohortMembershipId = Objects.requireNonNull(
                cohortMembershipId,
                "cohortMembershipId"
        );
        timerRun.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        return timerRun;
    }

    public boolean isRunning() {
        return endedAt == null && measuredSeconds == null && endReason == null;
    }

    public void stop(Instant endedAt) {
        Instant newEndedAt = validateEndTime(endedAt);
        long elapsedSeconds = Duration.between(startedAt, newEndedAt).getSeconds();

        finish(newEndedAt, elapsedSeconds, TimerEndReason.STOP);
    }

    public void discard(Instant endedAt) {
        finish(validateEndTime(endedAt), null, TimerEndReason.DISCARD);
    }

    public void expire(Instant expiredAt) {
        finish(validateEndTime(expiredAt), null, TimerEndReason.EXPIRED);
    }

    private Instant validateEndTime(Instant endedAt) {
        requireRunning();
        Instant newEndedAt = Objects.requireNonNull(endedAt, "endedAt");

        if (newEndedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }

        return newEndedAt;
    }

    private void finish(
            Instant endedAt,
            Long measuredSeconds,
            TimerEndReason endReason
    ) {
        this.endedAt = endedAt;
        this.measuredSeconds = measuredSeconds;
        this.endReason = endReason;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("종료된 타이머 실행은 변경할 수 없습니다.");
        }
    }
}
