package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 출결 기록 response
 *
 * <p>{@code cohortMembershipId}·{@code userId}·{@code nickname}은 관리자 화면이 행과
 * 구성원을 잇는 데 쓴다. 이 셋이 없으면 화면은 "출결 기록 #43"처럼 기록 식별자밖에
 * 그릴 수 없다. 본인 조회·입퇴실 응답에서는 계정 두 필드가 {@code null}이며, 대상이
 * 요청자 자신이라 화면이 채울 필요가 없다.</p>
 */
public record AttendanceRecordResponse(
        Long id,
        Long cohortMembershipId,
        UUID userId,
        String nickname,
        LocalDate attendanceDate,
        AttendanceStatus autoStatus,
        AttendanceStatus finalStatus,
        Instant checkedInAt,
        Instant checkedOutAt,
        Integer lateMinutes,
        Integer earlyLeaveMinutes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static AttendanceRecordResponse from(AttendanceRecordResult result) {
        return new AttendanceRecordResponse(
                result.id(),
                result.cohortMembershipId(),
                result.userId(),
                result.nickname(),
                result.attendanceDate(),
                result.autoStatus(),
                result.finalStatus(),
                result.checkedInAt(),
                result.checkedOutAt(),
                result.lateMinutes(),
                result.earlyLeaveMinutes(),
                result.version(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
