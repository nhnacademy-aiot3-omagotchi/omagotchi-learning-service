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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 출결 기록 entity
 */
@Entity
@Table(name = "attendance_records", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_status", nullable = false, length = 20)
    private AttendanceStatus autoStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 20)
    private AttendanceStatus finalStatus;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes;

    @Column(name = "early_leave_minutes", nullable = false)
    private Integer earlyLeaveMinutes;

    @Version
    @Column(nullable = false)
    private Short version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AttendanceRecord start(
            Long cohortMembershipId,
            LocalDate attendanceDate
    ) {
        AttendanceRecord record = new AttendanceRecord();
        record.cohortMembershipId = cohortMembershipId;
        record.attendanceDate = attendanceDate;
        record.autoStatus = AttendanceStatus.PENDING;
        record.finalStatus = AttendanceStatus.PENDING;
        record.lateMinutes = 0;
        record.earlyLeaveMinutes = 0;
        return record;
    }

    public void checkIn(Instant checkedInAt, AttendanceStatus autoStatus, int lateMinutes) {
        if (this.checkedInAt != null) {
            throw new IllegalStateException("이미 입실 처리된 출결 기록입니다.");
        }
        this.checkedInAt = checkedInAt;
        this.autoStatus = autoStatus;
        this.finalStatus = autoStatus;
        this.lateMinutes = lateMinutes;
    }

    public void checkOut(Instant checkedOutAt, AttendanceStatus autoStatus, int earlyLeaveMinutes) {
        if (this.checkedOutAt != null) {
            throw new IllegalStateException("이미 퇴실 처리된 출결 기록입니다.");
        }
        this.checkedOutAt = checkedOutAt;
        this.autoStatus = autoStatus;
        this.finalStatus = autoStatus;
        this.earlyLeaveMinutes = earlyLeaveMinutes;
    }

    public void markAbsent() {
        this.autoStatus = AttendanceStatus.ABSENT;
        this.finalStatus = AttendanceStatus.ABSENT;
    }

    public void markMissingCheckOut() {
        this.autoStatus = AttendanceStatus.MISSING_CHECK_OUT;
        this.finalStatus = AttendanceStatus.MISSING_CHECK_OUT;
    }

    public void overrideFinalStatus(AttendanceStatus nextStatus) {
        this.finalStatus = nextStatus;
    }
}
