package site.omagotchi.learningservice.attendance.domain;

/**
 * 출석 상태
 */
public enum AttendanceStatus {
    PENDING, // 보류중
    PRESENT, // 현재
    LATE, // 지각
    ABSENT, // 결석
    LEFT_EARLY, // 조퇴
    MISSING_CHECK_OUT // 퇴실 체크 깜빡함
}
