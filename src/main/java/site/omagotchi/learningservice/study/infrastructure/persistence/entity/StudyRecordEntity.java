package site.omagotchi.learningservice.study.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "study_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class StudyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "aggregation_date", nullable = false)
    private LocalDate aggregationDate;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "study_seconds", nullable = false)
    private Long studySeconds;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_cohort_membership_id")
    private Long deletedByCohortMembershipId;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public StudyRecordEntity(
            Long cohortMembershipId,
            LocalDate aggregationDate,
            Instant startTime,
            Instant endTime,
            Long studySeconds
    ) {
        this.cohortMembershipId = cohortMembershipId;
        this.aggregationDate = aggregationDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.studySeconds = studySeconds;
    }

    public void applyUpdate(
            LocalDate aggregationDate,
            Instant startTime,
            Instant endTime,
            Long studySeconds
    ) {
        this.aggregationDate = aggregationDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.studySeconds = studySeconds;
    }

    public void applySoftDelete(Instant deletedAt, Long deletedByCohortMembershipId) {
        this.deletedAt = deletedAt;
        this.deletedByCohortMembershipId = deletedByCohortMembershipId;
    }
}
