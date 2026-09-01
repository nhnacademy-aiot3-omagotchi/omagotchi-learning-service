package site.omagotchi.learningservice.attendance.application;

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
    ),
    PRESENCE_TRANSITION_NOT_ALLOWED(
            ErrorType.CONFLICT,
            "PRESENCE_TRANSITION_NOT_ALLOWED",
            "현재 출결 상태에서는 체류 구간을 전환할 수 없습니다."
    ),
    PRESENCE_INTERVAL_INCONSISTENT(
            ErrorType.CONFLICT,
            "PRESENCE_INTERVAL_INCONSISTENT",
            "열린 체류 구간이 중복되어 전환할 수 없습니다."
    ),
    PRESENCE_ACTIVE_INTERVAL_REQUIRED(
            ErrorType.CONFLICT,
            "PRESENCE_ACTIVE_INTERVAL_REQUIRED",
            "현재 열린 체류 구간이 필요합니다."
    ),
    PRESENCE_STATE_MISMATCH(
            ErrorType.CONFLICT,
            "PRESENCE_STATE_MISMATCH",
            "현재 체류 상태 또는 공간이 요청과 일치하지 않습니다."
    ),
    PRESENCE_MEMBERSHIP_MISMATCH(
            ErrorType.CONFLICT,
            "PRESENCE_MEMBERSHIP_MISMATCH",
            "체류 전환 대상 소속이 출결 기록과 일치하지 않습니다."
    ),
    PRESENCE_RETURN_SPACE_NOT_FOUND(
            ErrorType.CONFLICT,
            "PRESENCE_RETURN_SPACE_NOT_FOUND",
            "회의 종료 후 복귀할 공간을 찾을 수 없습니다."
    ),
    PRESENCE_INVALID_SPACE_ID(
            ErrorType.INVALID_INPUT,
            "PRESENCE_INVALID_SPACE_ID",
            "체류 공간 ID가 올바르지 않습니다."
    ),
    PRESENCE_INVALID_TIME(
            ErrorType.CONFLICT,
            "PRESENCE_INVALID_TIME",
            "체류 종료 시각은 시작 시각보다 빠를 수 없습니다."
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
