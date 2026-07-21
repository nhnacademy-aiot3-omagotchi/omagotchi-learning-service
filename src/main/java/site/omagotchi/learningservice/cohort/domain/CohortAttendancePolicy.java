package site.omagotchi.learningservice.cohort.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 기수별 출결 판정 기준 시간을 관리
 * 시작, 종료, 결석 처리 기준 시각과 허용 자리비움 시간을 기수 단위로 분리
 */
@Getter
@Entity
@Table(name = "cohort_attendance_policies", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortAttendancePolicy {

    @Id
    @Column(name = "cohort_id")
    private Long cohortId;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time", nullable = false)
    private LocalTime scheduledEndTime;

    @Column(name = "absence_cutoff_time")
    private LocalTime absenceCutoffTime;

    @Column(name = "allowed_away_minutes", nullable = false)
    private Integer allowedAwayMinutes;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static CohortAttendancePolicy create(
            Long cohortId,
            String timezone,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            LocalTime absenceCutoffTime,
            Integer allowedAwayMinutes,
            Long updatedByUserId
    ) {
        validateTimes(scheduledStartTime, scheduledEndTime, absenceCutoffTime);
        validateAllowedAwayMinutes(allowedAwayMinutes);

        CohortAttendancePolicy policy = new CohortAttendancePolicy();
        policy.cohortId = cohortId;
        policy.timezone = requireText(timezone, "timezone");
        policy.scheduledStartTime = scheduledStartTime;
        policy.scheduledEndTime = scheduledEndTime;
        policy.absenceCutoffTime = absenceCutoffTime;
        policy.allowedAwayMinutes = allowedAwayMinutes;
        policy.updatedByUserId = updatedByUserId;
        return policy;
    }

    public void update(
            String timezone,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            LocalTime absenceCutoffTime,
            Integer allowedAwayMinutes,
            Long updatedByUserId
    ) {
        validateTimes(scheduledStartTime, scheduledEndTime, absenceCutoffTime);
        validateAllowedAwayMinutes(allowedAwayMinutes);

        this.timezone = requireText(timezone, "timezone");
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.absenceCutoffTime = absenceCutoffTime;
        this.allowedAwayMinutes = allowedAwayMinutes;
        this.updatedByUserId = updatedByUserId;
    }

    private static void validateTimes(
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            LocalTime absenceCutoffTime
    ) {
        if (scheduledStartTime == null || scheduledEndTime == null) {
            throw new IllegalArgumentException("출결 정책 시간은 필수입니다.");
        }
        if (!scheduledStartTime.isBefore(scheduledEndTime)) {
            throw new IllegalArgumentException("출석 시작 시각은 종료 시각보다 빨라야 합니다.");
        }
    }

    private static void validateAllowedAwayMinutes(Integer allowedAwayMinutes) {
        if (allowedAwayMinutes == null || allowedAwayMinutes < 0) {
            throw new IllegalArgumentException("허용 자리비움 시간은 0분 이상이어야 합니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
