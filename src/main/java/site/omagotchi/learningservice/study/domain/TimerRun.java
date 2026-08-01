package site.omagotchi.learningservice.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    public boolean isRunningAt(Instant currentAt, TimerTimePolicy timePolicy) {
        validateTimePolicy(timePolicy);
        validateCurrentTime(currentAt);

        return isRunning() && !timePolicy.isExpired(startedAt, currentAt);
    }

    public TimerEndReason stopOrExpire(
            Instant currentAt,
            TimerTimePolicy timePolicy
    ) {
        validateEndRequest(currentAt, timePolicy);

        if (expireIfDue(currentAt, timePolicy)) {
            return TimerEndReason.EXPIRED;
        }

        long elapsedSeconds = timePolicy.elapsedSeconds(startedAt, currentAt);
        return endTimer(currentAt, elapsedSeconds, TimerEndReason.STOP);
    }

    public TimerEndReason discardOrExpire(
            Instant currentAt,
            TimerTimePolicy timePolicy
    ) {
        validateEndRequest(currentAt, timePolicy);

        if (expireIfDue(currentAt, timePolicy)) {
            return TimerEndReason.EXPIRED;
        }

        return endTimer(currentAt, null, TimerEndReason.DISCARD);
    }

    public boolean expireIfDue(
            Instant currentAt,
            TimerTimePolicy timePolicy
    ) {
        validateTimePolicy(timePolicy);
        validateCurrentTime(currentAt);

        if (!isRunning() || !timePolicy.isExpired(startedAt, currentAt)) {
            return false;
        }

        expireAt(timePolicy.expirationAt(startedAt));
        return true;
    }

    // ===== Private Methods =====

    private void validateEndRequest(
            Instant currentAt,
            TimerTimePolicy timePolicy
    ) {
        validateTimePolicy(timePolicy);
        validateCurrentTime(currentAt);

        if (!isRunning()) {
            throw new IllegalStateException("종료된 타이머 실행은 변경할 수 없습니다.");
        }

        if (currentAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private void validateTimePolicy(TimerTimePolicy timePolicy) {
        if (timePolicy == null) {
            throw new IllegalArgumentException("timePolicy가 null입니다.");
        }
    }

    private void validateCurrentTime(Instant currentAt) {
        if (currentAt == null) {
            throw new IllegalArgumentException("currentAt이 null입니다.");
        }
    }

    private TimerEndReason expireAt(Instant expirationAt) {
        return endTimer(expirationAt, null, TimerEndReason.EXPIRED);
    }

    private TimerEndReason endTimer(
            Instant endedAt,
            Long measuredSeconds,
            TimerEndReason endReason
    ) {
        this.endedAt = endedAt;
        this.measuredSeconds = measuredSeconds;
        this.endReason = endReason;
        return endReason;
    }
}
