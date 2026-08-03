package site.omagotchi.learningservice.attendance.application.result;

import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 사용자 출결 기록
 */
public record AttendanceRecordResult(
        Long id, // 출석 기록 Id
        Long cohortMembershipId, // 기수 소속 Id
        LocalDate attendanceDate, // 출석 일자
        AttendanceStatus autoStatus, // 자동 판정 상태
        AttendanceStatus finalStatus, // 최종 판정 상태
        Instant checkedInAt, // 입실 시각
        Instant checkedOutAt, // 퇴실 시각
        Integer lateMinutes, // 지각 시각
        Integer earlyLeaveMinutes, // 조퇴 시각
        Short version, // 버전
        Instant createdAt, // 생성 시각
        Instant updatedAt // 수정 시각
) {

    public static AttendanceRecordResult from(AttendanceRecord record) {
        return new AttendanceRecordResult(
                record.getId(),
                record.getCohortMembershipId(),
                record.getAttendanceDate(),
                record.getAutoStatus(),
                record.getFinalStatus(),
                record.getCheckedInAt(),
                record.getCheckedOutAt(),
                record.getLateMinutes(),
                record.getEarlyLeaveMinutes(),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}
