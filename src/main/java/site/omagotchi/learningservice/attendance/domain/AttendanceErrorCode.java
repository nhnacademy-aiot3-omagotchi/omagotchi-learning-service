package site.omagotchi.learningservice.attendance.domain;

import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.global.exception.ErrorType;

/**
 * error code
 */

@RequiredArgsConstructor
public enum AttendanceErrorCode implements ErrorCode {

    ATTENDANCE_POLICY_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ATTENDANCE_POLICY_NOT_FOUND",
            "출결 정책을 찾을 수 없습니다."
    ),
    ATTENDANCE_RECORD_NOT_FOUND(
            ErrorType.NOT_FOUND,
            "ATTENDANCE_RECORD_NOT_FOUND",
            "출결 기록을 찾을 수 없습니다."
    ),
    ATTENDANCE_ALREADY_CHECKED_IN(
            ErrorType.CONFLICT,
            "ATTENDANCE_ALREADY_CHECKED_IN",
            "이미 출석 처리된 날짜입니다."
    ),
    ATTENDANCE_ALREADY_CHECKED_OUT(
            ErrorType.CONFLICT,
            "ATTENDANCE_ALREADY_CHECKED_OUT",
            "이미 퇴실 처리된 날짜입니다."
    ),
    ATTENDANCE_CHECK_IN_REQUIRED(
            ErrorType.CONFLICT,
            "ATTENDANCE_CHECK_IN_REQUIRED",
            "입실 기록이 필요합니다."
    ),
    ATTENDANCE_CHANGE_REASON_REQUIRED(
            ErrorType.INVALID_INPUT,
            "ATTENDANCE_CHANGE_REASON_REQUIRED",
            "출결 변경 사유는 필수입니다."
    ),
    ATTENDANCE_INVALID_PAGE_REQUEST(
            ErrorType.INVALID_INPUT,
            "ATTENDANCE_INVALID_PAGE_REQUEST",
            "출결 조회 조건이 올바르지 않습니다."
    );

    private final ErrorType type;
    private final String code;
    private final String message;

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
