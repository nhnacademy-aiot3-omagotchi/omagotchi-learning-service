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
    private Long version;

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

    public void applyDecision(AttendanceDecision decision) {
        this.autoStatus = decision.status();
        this.finalStatus = decision.status();
        this.lateMinutes = decision.lateMinutes();
        this.earlyLeaveMinutes = decision.earlyLeaveMinutes();
    }

    public void markAbsent() {
        this.autoStatus = AttendanceStatus.ABSENT;
        this.finalStatus = AttendanceStatus.ABSENT;
    }

    /**
     * 체크인은 했지만 정상 퇴실하지 못한 출결을 관리자 확인 대상으로 확정한다.
     *
     * <p>{@code checkedOutAt}은 채우지 않는다. 실제 퇴실 시각을 알 수 없다는 사실을
     * 보존하고, 현재 재실 여부는 별도의 체류 구간 마감으로 해소한다.</p>
     *
     * <p>정합성 정리와 이벤트 재수신에서 반복 호출될 수 있으므로 멱등하다. 특히 이미
     * {@code MISSING_CHECK_OUT}으로 자동 판정된 뒤 관리자가 최종 상태를 교정했다면,
     * 재실행이 그 교정을 다시 덮어쓰지 않는다.</p>
     *
     * @return 이번 호출로 상태를 변경했으면 {@code true}
     */
    public boolean markMissingCheckOut() {
        if (checkedInAt == null
                || checkedOutAt != null
                || autoStatus == AttendanceStatus.MISSING_CHECK_OUT) {
            return false;
        }
        this.autoStatus = AttendanceStatus.MISSING_CHECK_OUT;
        this.finalStatus = AttendanceStatus.MISSING_CHECK_OUT;
        return true;
    }

    public void overrideFinalStatus(AttendanceStatus nextStatus) {
        this.finalStatus = nextStatus;
    }
}
