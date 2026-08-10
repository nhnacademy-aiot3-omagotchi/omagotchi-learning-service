package site.omagotchi.learningservice.ranking.domain;

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
import java.time.LocalDate;

@Entity
@Table(name = "ranking_snapshots", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false, updatable = false)
    private Long cohortId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RankingPeriod period;

    @Column(name = "base_date", nullable = false, updatable = false)
    private LocalDate baseDate;

    @Column(name = "range_start_date", nullable = false, updatable = false)
    private LocalDate rangeStartDate;

    @Column(name = "range_end_date", nullable = false, updatable = false)
    private LocalDate rangeEndDate;

    @CreatedDate
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    public static RankingSnapshot create(Long cohortId, RankingPeriod period, LocalDate baseDate, RankingDateRange range) {
        RankingSnapshot snapshot = new RankingSnapshot();
        snapshot.cohortId = cohortId;
        snapshot.period = period;
        snapshot.baseDate = baseDate;
        snapshot.rangeStartDate = range.startDate();
        snapshot.rangeEndDate = range.endDate();
        return snapshot;
    }
}
