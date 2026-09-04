package site.omagotchi.learningservice.attendance.application.result;

import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자 출결 기록
 *
 * <p>{@code userId}와 {@code nickname}은 <b>선택</b>이다. 출결 행 자체는 소속 식별자만
 * 들고 있어 계정 정보를 알지 못하므로, 관리자 목록처럼 "누구의 기록인가"를 화면에
 * 그려야 하는 경로에서만 호출부가 채워 넣는다. 본인 조회·입퇴실 응답은 대상이 이미
 * 요청자 자신이라 비워 둔다.</p>
 *
 * <p>비어 있을 수 있는 값을 필수로 올리지 않는 것이 의도다. 필수로 만들면 계정 조회가
 * 실패하는 순간 출결 목록 전체가 실패하는데, 관리자에게는 <b>이름 없는 출결</b>이
 * <b>출결 없음</b>보다 낫다.</p>
 */
public record AttendanceRecordResult(
        Long id, // 출석 기록 Id
        Long cohortMembershipId, // 기수 소속 Id
        UUID userId, // 계정 Id (선택)
        String nickname, // 표시 이름 (선택)
        LocalDate attendanceDate, // 출석 일자
        AttendanceStatus autoStatus, // 자동 판정 상태
        AttendanceStatus finalStatus, // 최종 판정 상태
        Instant checkedInAt, // 입실 시각
        Instant checkedOutAt, // 퇴실 시각
        Integer lateMinutes, // 지각 시각
        Integer earlyLeaveMinutes, // 조퇴 시각
        Long version, // 버전
        Instant createdAt, // 생성 시각
        Instant updatedAt // 수정 시각
) {

    /** 계정 정보를 함께 조회하지 않은 호출부용. 화면은 소속 식별자로만 구분한다. */
    public static AttendanceRecordResult from(AttendanceRecord record) {
        return from(record, null, null);
    }

    public static AttendanceRecordResult from(AttendanceRecord record, UUID userId, String nickname) {
        return new AttendanceRecordResult(
                record.getId(),
                record.getCohortMembershipId(),
                userId,
                nickname,
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
