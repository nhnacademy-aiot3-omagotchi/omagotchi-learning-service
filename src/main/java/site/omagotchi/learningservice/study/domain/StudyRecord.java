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
        if (cohortMembershipId == null) {
            throw new IllegalArgumentException("cohortMembershipId가 null입니다.");
        }
        
        StudyRecord studyRecord = new StudyRecord();
        studyRecord.cohortMembershipId = cohortMembershipId;
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
        if (deletedAt == null) {
            throw new IllegalArgumentException("deletedAt이 null입니다.");
        }
        this.deletedAt = deletedAt;
    }

    private void replaceTimeRange(
            Instant startTime,
            Instant endTime,
            long studySeconds
    ) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("시간 입력이 누락되었습니다.");
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("시작 시각이 종료 시각보다 빠릅니다.");
        }
        if (StudyTimePolicy.crossesAggregationBoundary(startTime, endTime)) {
            throw new IllegalArgumentException("공부 기록이 집계 경계와 겹쳐있습니다.");
        }

        long occupiedSeconds = Duration.between(startTime, endTime).getSeconds();
        if (studySeconds <= 0 || studySeconds > occupiedSeconds) {
            throw new IllegalArgumentException("공부 시간은 0초보다 크고 점유 구간 이하여야 합니다.");
        }

        this.aggregationDate = StudyTimePolicy.aggregationDate(startTime);
        this.startTime = startTime;
        this.endTime = endTime;
        this.studySeconds = studySeconds;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new IllegalStateException("삭제된 공부 기록은 변경할 수 없습니다.");
        }
    }
}
