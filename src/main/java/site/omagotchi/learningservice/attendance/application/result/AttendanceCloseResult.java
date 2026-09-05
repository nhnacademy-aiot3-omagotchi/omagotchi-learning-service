package site.omagotchi.learningservice.attendance.application.result;

/** 종료 소속의 미퇴실 확정 전, 열린 체류 구간을 잠금 안에서 검사한 결과. */
public enum AttendanceCloseResult {
    CLOSED,
    ALREADY_CLOSED,
    MEETING_OPEN
}
