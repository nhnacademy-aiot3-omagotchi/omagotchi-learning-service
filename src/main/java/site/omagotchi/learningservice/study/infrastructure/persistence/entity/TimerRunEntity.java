package site.omagotchi.learningservice.study.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "timer_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TimerRunEntity {

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

    @Builder
    public TimerRunEntity(Long cohortMembershipId, Instant startedAt) {
        this.cohortMembershipId = cohortMembershipId;
        this.startedAt = startedAt;
    }

    public boolean isRunning() {
        return endedAt == null;
    }

    public void applyStop(Instant endedAt, long measuredSeconds) {
        this.endedAt = endedAt;
        this.measuredSeconds = measuredSeconds;
        this.endReason = TimerEndReason.STOP;
    }

    public void applyDiscard(Instant endedAt) {
        this.endedAt = endedAt;
        this.measuredSeconds = null;
        this.endReason = TimerEndReason.DISCARD;
    }
}
