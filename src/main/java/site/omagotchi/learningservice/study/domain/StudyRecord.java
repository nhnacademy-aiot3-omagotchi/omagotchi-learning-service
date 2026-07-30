package site.omagotchi.learningservice.study.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "study_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class StudyRecord {

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

    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StudyRecord create(
            Long cohortMembershipId,
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {
        StudyRecord studyRecord = new StudyRecord();
        studyRecord.cohortMembershipId = Objects.requireNonNull(
                cohortMembershipId,
                "cohortMembershipId"
        );
        studyRecord.replaceTimeRange(startTime, endTime, studySeconds);
        return studyRecord;
    }

    public void updateTimeRange(
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {
        requireActive();
        replaceTimeRange(startTime, endTime, studySeconds);
    }

    public void softDelete(Instant deletedAt) {
        requireActive();
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt");
    }

    private void replaceTimeRange(
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {
        Instant newStartTime = Objects.requireNonNull(startTime, "startTime");
        Instant newEndTime = Objects.requireNonNull(endTime, "endTime");

        if (!newStartTime.isBefore(newEndTime)) {
            throw new IllegalArgumentException("시작 시각은 종료 시각보다 빨라야 합니다.");
        }
        if (StudyTimePolicy.crossesAggregationBoundary(newStartTime, newEndTime)) {
            throw new IllegalArgumentException("공부 기록은 집계 경계를 넘을 수 없습니다.");
        }

        long occupiedSeconds = Duration.between(newStartTime, newEndTime).getSeconds();
        if (studySeconds <= 0 || studySeconds > occupiedSeconds) {
            throw new IllegalArgumentException(
                    "공부 시간은 0초보다 크고 점유 구간 이하여야 합니다."
            );
        }

        this.aggregationDate = StudyTimePolicy.aggregationDate(newStartTime);
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.studySeconds = studySeconds;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new IllegalStateException("삭제된 공부 기록은 변경할 수 없습니다.");
        }
    }
}
